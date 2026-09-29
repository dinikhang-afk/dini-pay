import Foundation

// MARK: - Payment Mode

public enum PaymentMode: String, CaseIterable, Identifiable {
    case appStore = "App Store"
    case sandbox = "Sandbox"
    case direct = "Direct"

    public var id: String { rawValue }

    public var descriptionText: String {
        switch self {
        case .appStore:
            return "App Store — hiện sheet thanh toán chính thức của Apple (trừ tiền thật)."
        case .sandbox:
            return "Sandbox — thử nghiệm, không trừ tiền, cần Sandbox account."
        case .direct:
            return "Direct — inject receipt ngay lập tức, không popup."
        }
    }
}

// MARK: - IAP Item (flat model for Explore tab)

public struct IAPItem: Identifiable, Hashable {
    public let id: String
    public let appName: String
    public let appBundleId: String
    public let appIconSystem: String
    public let title: String
    public let formattedPrice: String
    public let rawPrice: Double
    public let isFree: Bool
    public let isTrial: Bool
    public let trialBadge: String?
    public let isHidden: Bool
    public let subtitle: String
    public var isStarred: Bool
    public let groupName: String
    public let family: String
    public let offerId: String?
    public let storeCountry: String
    public let productNumber: String?

    public init(
        id: String,
        appName: String,
        appBundleId: String,
        appIconSystem: String = "play.rectangle.fill",
        title: String,
        formattedPrice: String,
        rawPrice: Double = 0.0,
        isFree: Bool,
        isTrial: Bool,
        trialBadge: String? = nil,
        isHidden: Bool = false,
        subtitle: String,
        isStarred: Bool = false,
        groupName: String,
        family: String = "Pro",
        offerId: String? = nil,
        storeCountry: String = "VN",
        productNumber: String? = nil
    ) {
        self.id = id
        self.appName = appName
        self.appBundleId = appBundleId
        self.appIconSystem = appIconSystem
        self.title = title
        self.formattedPrice = formattedPrice
        self.rawPrice = rawPrice
        self.isFree = isFree
        self.isTrial = isTrial
        self.trialBadge = trialBadge
        self.isHidden = isHidden
        self.subtitle = subtitle
        self.isStarred = isStarred
        self.groupName = groupName
        self.family = family
        self.offerId = offerId
        self.storeCountry = storeCountry
        self.productNumber = productNumber
    }
}


// MARK: - Log Entry

public struct IAPLogEntry: Identifiable, Hashable {
    public let id = UUID()
    public let timestamp: Date
    public let level: String
    public let tag: String
    public let message: String

    public init(level: String, tag: String, message: String) {
        self.timestamp = Date()
        self.level = level
        self.tag = tag
        self.message = message
    }

    public var formattedTime: String {
        let df = DateFormatter()
        df.dateFormat = "HH:mm:ss.SSS"
        return df.string(from: timestamp)
    }
}

// MARK: - Product Type

public enum ProductType: String, Codable {
    case subs = "SUBS"
    case inapp = "INAPP"
}

// MARK: - Pricing Phase

public struct PricingPhase: Identifiable, Hashable, Codable {
    public var id: String { "\(billingPeriod)_\(formattedPrice)_\(billingCycleCount)" }
    public let formattedPrice: String
    public let billingPeriod: String
    public let billingCycleCount: Int

    public init(formattedPrice: String, billingPeriod: String, billingCycleCount: Int) {
        self.formattedPrice = formattedPrice
        self.billingPeriod = billingPeriod
        self.billingCycleCount = billingCycleCount
    }
}

// MARK: - Offer Classification

public enum OfferClassification: String, Codable {
    case regular = "REGULAR"
    case freeTrial = "FREE_TRIAL"
    case introDiscount = "INTRO_DISCOUNT"
}

// MARK: - Offer Model

public struct OfferModel: Identifiable, Hashable, Codable {
    public var id: String { offerId ?? summaryText }
    public let offerId: String?
    public let classification: OfferClassification
    public let summaryText: String
    public let pricingPhases: [PricingPhase]

    public init(offerId: String? = nil, classification: OfferClassification, summaryText: String, pricingPhases: [PricingPhase]) {
        self.offerId = offerId
        self.classification = classification
        self.summaryText = summaryText
        self.pricingPhases = pricingPhases
    }
}

// MARK: - Product Model (used inside ScanSnapshot)

public struct ProductModel: Identifiable, Hashable, Codable {
    public var id: String { productId }
    public let productId: String
    public let productType: ProductType
    public let title: String
    public let description: String
    public let formattedBasePrice: String
    public let hasFreeTrial: Bool
    public let hasIntroDiscount: Bool
    public let offers: [OfferModel]

    public init(
        productId: String,
        productType: ProductType = .inapp,
        title: String = "",
        description: String = "",
        formattedBasePrice: String = "N/A",
        hasFreeTrial: Bool = false,
        hasIntroDiscount: Bool = false,
        offers: [OfferModel] = []
    ) {
        self.productId = productId
        self.productType = productType
        self.title = title
        self.description = description
        self.formattedBasePrice = formattedBasePrice
        self.hasFreeTrial = hasFreeTrial
        self.hasIntroDiscount = hasIntroDiscount
        self.offers = offers
    }
}

// MARK: - Scan Snapshot (JSON written by Tweak, decoded by TweakBridge)

public struct ScanSnapshot: Identifiable, Hashable, Codable {
    public var id: String { "\(bundleId)_\(timestamp)" }
    public let bundleId: String
    public let timestamp: TimeInterval
    public let products: [ProductModel]

    public init(bundleId: String, timestamp: TimeInterval, products: [ProductModel]) {
        self.bundleId = bundleId
        self.timestamp = timestamp
        self.products = products
    }

    public var formattedDate: String {
        let df = DateFormatter()
        df.dateFormat = "dd/MM/yyyy HH:mm"
        return df.string(from: Date(timeIntervalSince1970: timestamp))
    }

    public var totalProducts: Int { products.count }
    public var totalSubscriptions: Int { products.filter { $0.productType == .subs }.count }
    public var totalFreeTrials: Int { products.filter { $0.hasFreeTrial }.count }
    public var totalDiscounts: Int { products.filter { $0.hasIntroDiscount }.count }
}

// MARK: - Diff Result

public struct DiffResult {
    public let addedProducts: [ProductModel]
    public let removedProducts: [ProductModel]
    public let modifiedProducts: [ProductDiffDetail]
    public let summaryChanges: [String]
}

public struct ProductDiffDetail: Identifiable, Hashable {
    public var id: String { productId }
    public let productId: String
    public let addedOffers: [String]
    public let removedOffers: [String]
    public let notes: [String]
}

// MARK: - Installed App Info

public struct InstalledAppInfo: Identifiable, Hashable {
    public var id: String { bundleId }
    public let bundleId: String
    public let appName: String
    public let version: String
    public let isSystemApp: Bool
    public let hasIAPSupport: Bool
    public let containerPath: String?
}

// MARK: - IAP Error

public enum IAPError: Error, LocalizedError {
    case failed(String)

    public var errorDescription: String? {
        switch self {
        case .failed(let msg): return msg
        }
    }
}

