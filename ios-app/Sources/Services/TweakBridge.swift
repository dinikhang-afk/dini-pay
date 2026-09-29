import Foundation

public final class TweakBridge: ObservableObject {
    public static let shared = TweakBridge()

    @Published public var snapshots: [ScanSnapshot] = []
    @Published public var lastSyncDate: Date? = nil

    private let fileManager = FileManager.default

    public init() {
        loadAllSnapshots()
    }

    private var searchPaths: [URL] {
        var paths: [URL] = []

        // 1. Jailbreak tweak global output path
        let jbPath = URL(fileURLWithPath: "/var/mobile/Documents/IAPCheck")
        paths.append(jbPath)

        // 2. App's local Documents/IAPCheck folder
        if let localDocs = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
            paths.append(localDocs.appendingPathComponent("IAPCheck"))
        }

        // 3. Fallback /tmp directory
        paths.append(URL(fileURLWithPath: "/tmp/IAPCheck"))

        return paths
    }

    public func loadAllSnapshots() {
        var foundSnapshots: [ScanSnapshot] = []

        for folder in searchPaths {
            guard fileManager.fileExists(atPath: folder.path) else { continue }

            do {
                let fileURLs = try fileManager.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
                for fileURL in fileURLs where fileURL.pathExtension == "json" {
                    if let data = try? Data(contentsOf: fileURL),
                       let snapshot = try? JSONDecoder().decode(ScanSnapshot.self, from: data) {
                        foundSnapshots.append(snapshot)
                    }
                }
            } catch {
                print("[TweakBridge] Error listing folder \(folder): \(error)")
            }
        }

        DispatchQueue.main.async {
            self.snapshots = foundSnapshots.sorted { $0.timestamp > $1.timestamp }
            self.lastSyncDate = Date()
        }
    }

    public func importJSONString(_ jsonString: String) -> Bool {
        guard let data = jsonString.data(using: .utf8) else { return false }
        do {
            let snapshot = try JSONDecoder().decode(ScanSnapshot.self, from: data)
            saveSnapshotLocally(snapshot)
            loadAllSnapshots()
            return true
        } catch {
            print("[TweakBridge] Failed to decode imported JSON: \(error)")
            return false
        }
    }

    public func saveSnapshotLocally(_ snapshot: ScanSnapshot) {
        guard let localDocs = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let folder = localDocs.appendingPathComponent("IAPCheck")
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)

        let fileURL = folder.appendingPathComponent("\(snapshot.bundleId)_\(snapshot.timestamp).json")
        if let data = try? JSONEncoder().encode(snapshot) {
            try? data.write(to: fileURL)
        }
    }

    public func triggerRemotePurchase(bundleId: String, productId: String) {
        let payload: [String: String] = [
            "bundleId": bundleId,
            "productId": productId,
            "timestamp": "\(Int(Date().timeIntervalSince1970))"
        ]

        if let data = try? JSONSerialization.data(withJSONObject: payload, options: .prettyPrinted) {
            // Write to /var/mobile/Documents/IAPCheck/pending_buy.json
            let jbFolder = URL(fileURLWithPath: "/var/mobile/Documents/IAPCheck")
            try? fileManager.createDirectory(at: jbFolder, withIntermediateDirectories: true)
            let jbFile = jbFolder.appendingPathComponent("pending_buy.json")
            try? data.write(to: jbFile)

            // Also write to local app Documents/IAPCheck/pending_buy.json
            if let localDocs = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
                let localFolder = localDocs.appendingPathComponent("IAPCheck")
                try? fileManager.createDirectory(at: localFolder, withIntermediateDirectories: true)
                let localFile = localFolder.appendingPathComponent("pending_buy.json")
                try? data.write(to: localFile)
            }
        }

        // Post Darwin Notification to wake up tweak inside target app
        let notificationName = CFNotificationName("com.adr.checkiap.trigger_buy" as CFString)
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), notificationName, nil, nil, true)

        // Launch target app so it receives the event and opens StoreKit
        InstalledAppsScanner.shared.launchApp(bundleId: bundleId)
    }

    public func deleteSnapshot(_ snapshot: ScanSnapshot) {
        // Remove from in-memory list
        snapshots.removeAll { $0.id == snapshot.id }

        // Try to remove matching files from disk
        for folder in searchPaths {
            guard fileManager.fileExists(atPath: folder.path) else { continue }
            do {
                let files = try fileManager.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
                for file in files where file.pathExtension == "json" {
                    if file.lastPathComponent.contains(snapshot.bundleId) {
                        try? fileManager.removeItem(at: file)
                    }
                }
            } catch {}
        }
    }
}

