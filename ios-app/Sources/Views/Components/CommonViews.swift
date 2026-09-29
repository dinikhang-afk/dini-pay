import SwiftUI

public struct MetricCard: View {
    public let title: String
    public let value: String
    public let subtitle: String
    public let accentColor: Color
    public let systemImage: String

    public init(title: String, value: String, subtitle: String, accentColor: Color, systemImage: String) {
        self.title = title
        self.value = value
        self.subtitle = subtitle
        self.accentColor = accentColor
        self.systemImage = systemImage
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(title.uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.textMuted)

                Spacer()

                Image(systemName: systemImage)
                    .font(.system(size: 14))
                    .foregroundColor(accentColor)
            }

            Text(value)
                .font(.system(size: 26, weight: .heavy, design: .rounded))
                .foregroundColor(.textPrimary)

            Text(subtitle)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.textSecondary)
        }
        .padding(14)
        .background(Color.darkCard)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.darkBorder, lineWidth: 1)
        )
    }
}

public struct BadgeTag: View {
    public let text: String
    public let backgroundColor: Color
    public let textColor: Color

    public init(_ text: String, bg: Color, fg: Color = .white) {
        self.text = text
        self.backgroundColor = bg
        self.textColor = fg
    }

    public var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(textColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(backgroundColor.opacity(0.85))
            .cornerRadius(6)
    }
}

public struct SectionHeaderView: View {
    public let title: String
    public let count: Int?

    public init(_ title: String, count: Int? = nil) {
        self.title = title
        self.count = count
    }

    public var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.textPrimary)

            if let c = count {
                Text("\(c)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.accentBlue)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.accentBlue.opacity(0.15))
                    .clipShape(Capsule())
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}
