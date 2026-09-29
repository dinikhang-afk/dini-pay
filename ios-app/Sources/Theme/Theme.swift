import SwiftUI

extension Color {
    // Exact IAPPay Dark Palette from Video IMG_0315.MOV
    static let iappayBackground = Color(hex: 0x0C0D11)
    static let iappaySurface = Color(hex: 0x14151B)
    static let iappayCard = Color(hex: 0x1A1B22)
    static let iappayBorder = Color(hex: 0x2A2B35)

    // Primary Accents
    static let iappayPurple = Color(hex: 0x6E56CF) // Main action button & primary highlight
    static let iappayPurpleDark = Color(hex: 0x4832A8)
    static let iappayGreen = Color(hex: 0x30D158)  // DÙNG THỬ / Free Trial badge
    static let iappayYellow = Color(hex: 0xFFD60A) // MIỄN PHÍ pill button
    static let iappayOrange = Color(hex: 0xFF9F0A) // GIẢM GIÁ
    static let iappayRed = Color(hex: 0xFF453A)    // Failed / Error

    // Badges
    static let iappayBadgeHidden = Color(hex: 0x2C2D35) // ẨN badge background
    static let iappayBadgeTrialBg = Color(hex: 0x183726) // DÙNG THỬ 30 NGÀY dark green bg

    // Text colors
    static let iappayTextPrimary = Color(hex: 0xFFFFFF)
    static let iappayTextSecondary = Color(hex: 0x8E8E93)
    static let iappayTextMuted = Color(hex: 0x636366)

    // MARK: - Short Aliases (used by all Views)

    static let darkBackground = iappayBackground
    static let darkSurface = iappaySurface
    static let darkCard = iappayCard
    static let darkBorder = iappayBorder

    static let accentBlue = iappayPurple
    static let purpleTrial = iappayGreen
    static let successGreen = iappayGreen
    static let errorRed = iappayRed
    static let warningYellow = iappayYellow

    static let textPrimary = iappayTextPrimary
    static let textSecondary = iappayTextSecondary
    static let textMuted = iappayTextMuted

    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
