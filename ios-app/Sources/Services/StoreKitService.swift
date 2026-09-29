import Foundation
import UIKit
import StoreKit

public final class StoreKitService: ObservableObject {
    public static let shared = StoreKitService()

    @Published public var items: [IAPItem] = []
    @Published public var logs: [IAPLogEntry] = []
    @Published public var isProcessing: Bool = false
    @Published public var isScanning: Bool = false

    private let bridge = TweakBridge.shared
    private let fileManager = FileManager.default

    public init() {
        loadRealData()
    }

    public func addLog(level: String = "INFO", tag: String = "IAPPay", message: String) {
        DispatchQueue.main.async {
            self.logs.insert(IAPLogEntry(level: level, tag: tag, message: message), at: 0)
        }
    }

    // MARK: - Load Real IAP Data from Tweak Output

    /// Scan all JSON files written by the tweak hook inside each target app
    public func loadRealData() {
        isScanning = true
        addLog(message: "Bắt đầu quét dữ liệu IAP thật từ tweak output...")

        DispatchQueue.global(qos: .userInitiated).async {
            var allItems: [IAPItem] = []
            let searchPaths: [String] = [
                "/var/mobile/Documents/IAPCheck",
                "/var/jb/var/mobile/Documents/IAPCheck", // rootless jailbreak path
                "/tmp/IAPCheck"
            ]

            // Also check app's own Documents/IAPCheck
            if let localDocs = self.fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
                let localPath = localDocs.appendingPathComponent("IAPCheck").path
                self.scanFolder(path: localPath, into: &allItems)
            }

            for path in searchPaths {
                self.scanFolder(path: path, into: &allItems)
            }

            DispatchQueue.main.async {
                self.items = allItems
                self.isScanning = false
                self.addLog(message: "Quét xong: tìm thấy \(allItems.count) gói IAP thật từ \(Set(allItems.map { $0.appBundleId }).count) ứng dụng")

                if allItems.isEmpty {
                    self.addLog(level: "WARN", message: "Không tìm thấy dữ liệu IAP. Hãy mở các ứng dụng trên iPhone (đã cài tweak IAPCheck) để hook StoreKit và thu thập gói IAP thật.")
                }

                // Fetch app metadata (icons, names) from iTunes API for discovered bundle IDs
                let bundleIds = Set(allItems.map { $0.appBundleId })
                for bundleId in bundleIds {
                    self.fetchAppMetadata(bundleId: bundleId)
                }
            }
        }
    }

    private func scanFolder(path: String, into items: inout [IAPItem]) {
        guard fileManager.fileExists(atPath: path) else { return }

        do {
            let fileURLs = try fileManager.contentsOfDirectory(
                at: URL(fileURLWithPath: path),
                includingPropertiesForKeys: nil
            )

            for fileURL in fileURLs where fileURL.pathExtension == "json" && !fileURL.lastPathComponent.contains("pending_buy") {
                guard let data = try? Data(contentsOf: fileURL) else { continue }
                guard let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }

                let parsedItems = parseTweakJSON(dict: dict, sourceFile: fileURL.lastPathComponent)
                // Deduplicate by product ID
                for item in parsedItems {
                    if !items.contains(where: { $0.id == item.id }) {
                        items.append(item)
                    }
                }
            }
        } catch {
            DispatchQueue.main.async {
                self.addLog(level: "ERROR", message: "Lỗi đọc thư mục \(path): \(error.localizedDescription)")
            }
        }
    }

    /// Parse the JSON format written by IAPProductModel.m in the tweak
    private func parseTweakJSON(dict: [String: Any], sourceFile: String) -> [IAPItem] {
        var result: [IAPItem] = []

        let bundleId = dict["bundleId"] as? String ?? "unknown"
        let products = dict["products"] as? [[String: Any]] ?? []

        for prod in products {
            let productId = prod["productId"] as? String ?? ""
            let productType = prod["productType"] as? String ?? "INAPP"
            let title = prod["title"] as? String ?? productId
            let description = prod["description"] as? String ?? ""
            let formattedBasePrice = prod["formattedBasePrice"] as? String ?? "N/A"
            let hasFreeTrial = prod["hasFreeTrial"] as? Bool ?? false
            let hasIntroDiscount = prod["hasIntroDiscount"] as? Bool ?? false
            let offers = prod["offers"] as? [[String: Any]] ?? []

            // Build subtitle from offers
            var subtitle = ""
            var trialBadge: String? = nil
            var isFree = false
            var isHidden = false

            for offer in offers {
                let classification = offer["classification"] as? String ?? "REGULAR"
                let summaryText = offer["summaryText"] as? String ?? ""
                let phases = offer["pricingPhases"] as? [[String: Any]] ?? []

                if classification == "FREE_TRIAL" {
                    isFree = true

                    // Extract trial duration from billing period
                    if let phase = phases.first {
                        let period = phase["billingPeriod"] as? String ?? ""
                        let cycles = phase["billingCycleCount"] as? Int ?? 1
                        let durationText = formatPeriodVietnamese(period, cycles: cycles)
                        trialBadge = "DÙNG THỬ \(durationText)"
                        subtitle = "Dùng thử \(durationText.lowercased())"
                    }
                } else if classification == "INTRO_DISCOUNT" {
                    if let phase = phases.first {
                        let price = phase["formattedPrice"] as? String ?? ""
                        let period = phase["billingPeriod"] as? String ?? ""
                        subtitle += (subtitle.isEmpty ? "" : " • ") + "Giảm giá: \(price)/\(formatPeriodShort(period))"
                    }
                }
            }

            // Check if this is a hidden offer (has trial/discount but not typically shown by the app)
            if hasFreeTrial || hasIntroDiscount {
                // Offers with trials or discounts that have offerId are promotional = hidden
                for offer in offers {
                    let offerId = offer["offerId"] as? String
                    if offerId != nil && !offerId!.isEmpty {
                        isHidden = true
                    }
                }
            }

            if subtitle.isEmpty {
                subtitle = description.isEmpty ? productId : description
            }

            if !isFree && formattedBasePrice != "N/A" {
                subtitle += (subtitle.isEmpty ? "" : " • ") + "sau đó \(formattedBasePrice)"
            }

            if isHidden {
                subtitle += " • Hidden"
            }

            // Determine group name
            let appName = cachedAppNames[bundleId] ?? bundleId.split(separator: ".").last.map(String.init) ?? bundleId
            let groupCount = products.count
            let groupName = "\(appName.uppercased()) • \(groupCount) gói"
            let iconName = guessSystemIcon(bundleId: bundleId, productType: productType)

            var detectedOfferId: String? = nil
            for offer in offers {
                if let oId = offer["offerId"] as? String, !oId.isEmpty {
                    detectedOfferId = oId
                    break
                }
            }

            let rawPriceDouble = Double(formattedBasePrice.filter { "0123456789.".contains($0) }) ?? 0.0
            let familyName = title.components(separatedBy: " ").first ?? "Pro"

            result.append(IAPItem(
                id: productId,
                appName: appName,
                appBundleId: bundleId,
                appIconSystem: iconName,
                title: title,
                formattedPrice: isFree ? "Miễn phí" : formattedBasePrice,
                rawPrice: rawPriceDouble,
                isFree: isFree,
                isTrial: hasFreeTrial,
                trialBadge: trialBadge,
                isHidden: isHidden,
                subtitle: subtitle,
                isStarred: false,
                groupName: groupName,
                family: familyName,
                offerId: detectedOfferId,
                storeCountry: "VN",
                productNumber: "\(productId.hashValue > 0 ? productId.hashValue : -productId.hashValue)"
            ))
        }

        return result
    }

    // MARK: - iTunes Lookup API for Real App Metadata

    private var cachedAppNames: [String: String] = [:]
    private var cachedAppIcons: [String: URL] = [:]

    private func fetchAppMetadata(bundleId: String) {
        let urlString = "https://itunes.apple.com/lookup?bundleId=\(bundleId)&country=vn"
        guard let url = URL(string: urlString) else { return }

        addLog(message: "Truy vấn iTunes API cho metadata app: \(bundleId)")

        URLSession.shared.dataTask(with: url) { data, _, error in
            guard let data = data, error == nil else { return }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
            guard let results = json["results"] as? [[String: Any]], let first = results.first else { return }

            let trackName = first["trackName"] as? String ?? bundleId
            let artworkUrl = first["artworkUrl100"] as? String

            DispatchQueue.main.async {
                self.cachedAppNames[bundleId] = trackName

                if let artworkUrl = artworkUrl, let iconURL = URL(string: artworkUrl) {
                    self.cachedAppIcons[bundleId] = iconURL
                }

                // Update existing items with real app name
                self.items = self.items.map { item in
                    if item.appBundleId == bundleId {
                        let groupCount = self.items.filter { $0.appBundleId == bundleId }.count
                        return IAPItem(
                            id: item.id,
                            appName: trackName,
                            appBundleId: item.appBundleId,
                            appIconSystem: item.appIconSystem,
                            title: item.title,
                            formattedPrice: item.formattedPrice,
                            rawPrice: item.rawPrice,
                            isFree: item.isFree,
                            isTrial: item.isTrial,
                            trialBadge: item.trialBadge,
                            isHidden: item.isHidden,
                            subtitle: item.subtitle,
                            isStarred: item.isStarred,
                            groupName: "\(trackName.uppercased()) • \(groupCount) gói",
                            family: item.family,
                            offerId: item.offerId,
                            storeCountry: item.storeCountry,
                            productNumber: item.productNumber
                        )
                    }
                    return item
                }

                self.addLog(message: "Metadata app: \(trackName) (\(bundleId))")
            }
        }.resume()
    }

    // MARK: - Purchase Execution

    // Direct in-app native StoreKit purchase (spoofed via storekitd hook)
    private var activeProductsRequest: SKProductsRequest?
    private var purchaseCompletion: ((Swift.Result<Void, IAPError>) -> Void)?

    public func executePurchase(item: IAPItem, mode: PaymentMode, completion: @escaping (Swift.Result<Void, IAPError>) -> Void) {
        isProcessing = true
        addLog(message: "Bắt đầu thanh toán [\(mode.rawValue)] trực tiếp cho gói: \(item.title) (\(item.id))")

        // 1. Write pending_buy.json with target bundle ID for storekitd spoofing
        writePendingBuy(bundleId: item.appBundleId, productId: item.id, mode: mode.rawValue.lowercased())

        switch mode {
        case .appStore, .sandbox:
            addLog(message: "Khởi tạo StoreKit Request trực tiếp ngay trong DiniPay (Target: \(item.appBundleId))...")
            self.purchaseCompletion = completion

            // Request product directly inside DiniPay
            let productIdentifiers = Set([item.id])
            self.activeProductsRequest = SKProductsRequest(productIdentifiers: productIdentifiers)
            
            // Perform request & checkout directly
            DispatchQueue.main.async {
                DirectPaymentHandler.shared.startPayment(
                    productId: item.id,
                    targetBundleId: item.appBundleId
                ) { result in
                    self.isProcessing = false
                    switch result {
                    case .success:
                        self.addLog(message: "Giao dịch StoreKit thành công ngay tại DiniPay!")
                        completion(.success(()))
                    case .failure(let err):
                        self.addLog(level: "ERROR", message: "Giao dịch thất bại: \(err.localizedDescription)")
                        completion(.failure(err))
                    }
                }
            }

        case .direct:
            addLog(message: "Inject receipt trực tiếp vào container: \(item.appBundleId)...")
            injectLocalReceipt(for: item)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                self.isProcessing = false
                self.addLog(message: "Receipt injected thành công.")
                completion(.success(()))
            }
        }
    }


    private func writePendingBuy(bundleId: String, productId: String, mode: String) {
        let payload: [String: String] = [
            "bundleId": bundleId,
            "productId": productId,
            "mode": mode,
            "timestamp": "\(Int(Date().timeIntervalSince1970))"
        ]

        let folder = URL(fileURLWithPath: "/var/mobile/Documents/IAPCheck")
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        let file = folder.appendingPathComponent("pending_buy.json")
        if let data = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted) {
            try? data.write(to: file)
        }
    }

    private func injectLocalReceipt(for item: IAPItem) {
        let payload: [String: Any] = [
            "bundle_id": item.appBundleId,
            "product_id": item.id,
            "is_trial_period": item.isTrial,
            "purchase_date": Int(Date().timeIntervalSince1970),
            "expires_date": Int(Date().addingTimeInterval(30 * 86400).timeIntervalSince1970),
            "status": "active"
        ]

        let folder = URL(fileURLWithPath: "/var/mobile/Documents/IAPCheck/Receipts")
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        let file = folder.appendingPathComponent("\(item.appBundleId)_receipt.json")
        if let data = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted) {
            try? data.write(to: file)
        }
        addLog(message: "Ghi receipt tại: \(file.path)")
    }

    // MARK: - Helpers

    private func formatPeriodVietnamese(_ period: String, cycles: Int) -> String {
        // ISO 8601 duration: P1W, P1M, P1Y, P3D, etc.
        let upper = period.uppercased()
        if upper.contains("D") {
            let days = extractNumber(from: upper) * cycles
            return "\(days) NGÀY"
        } else if upper.contains("W") {
            let weeks = extractNumber(from: upper) * cycles
            return "\(weeks * 7) NGÀY"
        } else if upper.contains("M") && !upper.contains("MIN") {
            let months = extractNumber(from: upper) * cycles
            return "\(months) THÁNG"
        } else if upper.contains("Y") {
            let years = extractNumber(from: upper) * cycles
            return "\(years) NĂM"
        }
        return period
    }

    private func formatPeriodShort(_ period: String) -> String {
        let upper = period.uppercased()
        if upper.contains("D") { return "\(extractNumber(from: upper))d" }
        if upper.contains("W") { return "\(extractNumber(from: upper))w" }
        if upper.contains("M") { return "\(extractNumber(from: upper))m" }
        if upper.contains("Y") { return "\(extractNumber(from: upper))y" }
        return period
    }

    private func extractNumber(from str: String) -> Int {
        let digits = str.filter { $0.isNumber }
        return Int(digits) ?? 1
    }

    private func guessSystemIcon(bundleId: String, productType: String) -> String {
        let lower = bundleId.lowercased()
        if lower.contains("youtube") { return "play.rectangle.fill" }
        if lower.contains("spotify") { return "music.note" }
        if lower.contains("netflix") { return "tv.fill" }
        if lower.contains("tinder") { return "flame.fill" }
        if lower.contains("canva") { return "paintpalette.fill" }
        if lower.contains("capcut") || lower.contains("lemon") { return "video.fill" }
        if lower.contains("music") { return "music.note" }
        if lower.contains("photo") || lower.contains("camera") { return "camera.fill" }
        if lower.contains("fitness") || lower.contains("health") { return "heart.fill" }
        if lower.contains("game") { return "gamecontroller.fill" }
        return productType == "SUBS" ? "repeat.circle.fill" : "app.fill"
    }
}
