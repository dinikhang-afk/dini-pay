import SwiftUI

public struct InstalledView: View {
    @ObservedObject var store = StoreKitService.shared
    @State private var selectedFilter: String = "Tất cả"
    @State private var searchText: String = ""
    @State private var selectedItemForPurchase: IAPItem? = nil
    @State private var showingLogs: Bool = false

    var allCount: Int { store.items.count }
    var trialCount: Int { store.items.filter { $0.isTrial }.count }
    var discountCount: Int { store.items.filter { $0.trialBadge != nil && !$0.isTrial }.count }

    var filteredItems: [IAPItem] {
        store.items.filter { item in
            let matchesFilter: Bool
            switch selectedFilter {
            case "Dùng thử":
                matchesFilter = item.isTrial
            case "Giảm giá":
                matchesFilter = item.trialBadge != nil && !item.isTrial
            default:
                matchesFilter = true
            }

            if searchText.isEmpty { return matchesFilter }
            return matchesFilter && (
                item.title.localizedCaseInsensitiveContains(searchText) ||
                item.subtitle.localizedCaseInsensitiveContains(searchText) ||
                item.appName.localizedCaseInsensitiveContains(searchText)
            )
        }
    }

    var groupedItems: [String: [IAPItem]] {
        Dictionary(grouping: filteredItems, by: { $0.groupName })
    }

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Top Header: Title & Logs Button
                        HStack {
                            Text("Đã cài đặt")
                                .font(.system(size: 22, weight: .bold))
                                .foregroundColor(.iappayTextPrimary)

                            Spacer()

                            Button(action: { showingLogs = true }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "list.bullet.rectangle")
                                        .font(.system(size: 14))
                                    Text("Logs")
                                        .font(.system(size: 14, weight: .semibold))
                                }
                                .foregroundColor(.iappayPurple)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.iappayCard)
                                .cornerRadius(16)
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.iappayBorder, lineWidth: 1))
                            }
                        }
                        .padding(.top, 8)

                        // 3 Metrics Box (Video 00:00)
                        HStack(spacing: 0) {
                            // Column 1: Total IAP
                            VStack(spacing: 4) {
                                Text("\(allCount)")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.iappayTextPrimary)
                                Text("GÓI IAP")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)

                            Divider().frame(height: 36).background(Color.iappayBorder)

                            // Column 2: Free Trial
                            VStack(spacing: 4) {
                                Text("\(trialCount)")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.iappayGreen)
                                Text("DÙNG THỬ")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)

                            Divider().frame(height: 36).background(Color.iappayBorder)

                            // Column 3: Discounts
                            VStack(spacing: 4) {
                                Text("\(discountCount)")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.iappayOrange)
                                Text("GIẢM GIÁ")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .padding(.vertical, 16)
                        .background(Color.iappayCard)
                        .cornerRadius(16)
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.iappayBorder, lineWidth: 1))

                        // Filter Pills
                        HStack(spacing: 8) {
                            FilterPill(title: "Tất cả (\(allCount))", isSelected: selectedFilter == "Tất cả") {
                                selectedFilter = "Tất cả"
                            }
                            FilterPill(title: "Dùng thử (\(trialCount))", isSelected: selectedFilter == "Dùng thử") {
                                selectedFilter = "Dùng thử"
                            }
                            FilterPill(title: "Giảm giá", isSelected: selectedFilter == "Giảm giá") {
                                selectedFilter = "Giảm giá"
                            }
                        }

                        // Search Input
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.iappayTextMuted)
                            TextField("Tìm gói in-app purchase...", text: $searchText)
                                .foregroundColor(.iappayTextPrimary)
                        }
                        .padding(12)
                        .background(Color.iappayCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.iappayBorder, lineWidth: 1))

                        // Grouped Items List
                        ForEach(groupedItems.keys.sorted(), id: \.self) { groupKey in
                            VStack(alignment: .leading, spacing: 10) {
                                // Group Header
                                Text(groupKey.uppercased())
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.iappayTextMuted)
                                    .padding(.top, 8)

                                ForEach(groupedItems[groupKey] ?? []) { item in
                                    IAPItemRow(item: item) {
                                        selectedItemForPurchase = item
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
            .navigationBarHidden(true)
            .sheet(item: $selectedItemForPurchase) { item in
                PurchaseConfirmSheet(item: item, isPresented: Binding(
                    get: { selectedItemForPurchase != nil },
                    set: { if !$0 { selectedItemForPurchase = nil } }
                ))
            }
            .sheet(isPresented: $showingLogs) {
                LogsView()
            }
        }
    }
}

// MARK: - Row matching video 00:00 - 00:01
struct IAPItemRow: View {
    let item: IAPItem
    let onTapped: () -> Void

    var body: some View {
        Button(action: onTapped) {
            HStack(spacing: 12) {
                // App Icon
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.red)
                        .frame(width: 44, height: 44)
                    Image(systemName: item.appIconSystem)
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                }

                // Title & Badges
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(item.title)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.iappayTextPrimary)

                        if let trial = item.trialBadge {
                            Text(trial)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.iappayGreen)
                                .cornerRadius(4)
                        }

                        if item.isHidden {
                            Text("ẨN")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.iappayTextSecondary)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.iappayBadgeHidden)
                                .cornerRadius(4)
                        }
                    }

                    Text(item.subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(.iappayTextSecondary)
                        .lineLimit(1)
                }

                Spacer()

                // Price Pill Button
                Text(item.formattedPrice)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(item.isFree ? .black : .white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(item.isFree ? Color.iappayYellow : Color.iappayPurple)
                    .cornerRadius(14)

                // Favorite Star
                Image(systemName: item.isStarred ? "star.fill" : "star")
                    .font(.system(size: 14))
                    .foregroundColor(item.isStarred ? Color.iappayYellow : Color.iappayTextMuted)
            }
            .padding(12)
            .background(Color.iappayCard)
            .cornerRadius(14)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.iappayBorder, lineWidth: 1))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct FilterPill: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                .foregroundColor(isSelected ? .white : .iappayTextSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? Color.iappayPurple : Color.iappayCard)
                .cornerRadius(18)
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(isSelected ? Color.clear : Color.iappayBorder, lineWidth: 1))
        }
    }
}
