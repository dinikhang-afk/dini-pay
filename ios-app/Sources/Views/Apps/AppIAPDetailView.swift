import SwiftUI

public enum IAPSortOption: String, CaseIterable, Identifiable {
    case optimal = "Tối ưu (Mặc định)"
    case cheapToExpensive = "Giá: Rẻ đến Đắt (Bỏ Trial)"
    case expensiveToCheap = "Giá: Đắt đến Rẻ (Bỏ Trial)"
    case nameAZ = "Tên A-Z"

    public var id: String { rawValue }
}

public enum IAPFilterDisplay: String, CaseIterable, Identifiable {
    case all = "Tất cả"
    case hiddenOnly = "Chỉ gói ẩn"
    case publicOnly = "Chỉ gói hiển thị"

    public var id: String { rawValue }
}

public struct AppIAPDetailView: View {
    public let appName: String
    public let bundleId: String
    public let appIconSystem: String
    public let initialItems: [IAPItem]

    @Environment(\.presentationMode) var presentationMode
    @ObservedObject var store = StoreKitService.shared

    @State private var selectedFilter: String = "Tất cả"
    @State private var searchText: String = ""
    @State private var selectedSort: IAPSortOption = .optimal
    @State private var selectedDisplay: IAPFilterDisplay = .all
    @State private var selectedItemForPurchase: IAPItem? = nil
    @State private var selectedItemForSpec: IAPItem? = nil
    @State private var showingLogs: Bool = false

    var appItems: [IAPItem] {
        let current = store.items.filter { $0.appBundleId == bundleId }
        return current.isEmpty ? initialItems : current
    }

    var allCount: Int { appItems.count }
    var trialCount: Int { appItems.filter { $0.isTrial }.count }
    var discountCount: Int { appItems.filter { $0.trialBadge != nil && !$0.isTrial }.count }

    var processedItems: [IAPItem] {
        var list = appItems.filter { item in
            // Filter pills
            let matchesFilter: Bool
            switch selectedFilter {
            case "Dùng thử": matchesFilter = item.isTrial
            case "Giảm giá": matchesFilter = item.trialBadge != nil && !item.isTrial
            default: matchesFilter = true
            }
            guard matchesFilter else { return false }

            // Display filter
            switch selectedDisplay {
            case .hiddenOnly: if !item.isHidden { return false }
            case .publicOnly: if item.isHidden { return false }
            case .all: break
            }

            // Search filter
            if searchText.isEmpty { return true }
            return item.title.localizedCaseInsensitiveContains(searchText) ||
                   item.subtitle.localizedCaseInsensitiveContains(searchText) ||
                   item.formattedPrice.localizedCaseInsensitiveContains(searchText) ||
                   item.id.localizedCaseInsensitiveContains(searchText)
        }

        // Sorting
        switch selectedSort {
        case .optimal:
            list.sort { ($0.isTrial ? 0 : 1) < ($1.isTrial ? 0 : 1) }
        case .cheapToExpensive:
            list.sort { $0.rawPrice < $1.rawPrice }
        case .expensiveToCheap:
            list.sort { $0.rawPrice > $1.rawPrice }
        case .nameAZ:
            list.sort { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        }

        return list
    }

    var groupedProcessedItems: [String: [IAPItem]] {
        Dictionary(grouping: processedItems, by: { $0.groupName })
    }

    public var body: some View {
        ZStack {
            Color.iappayBackground.edgesIgnoringSafeArea(.all)

            VStack(spacing: 0) {
                // Header matching video
                HStack(spacing: 12) {
                    Button(action: { presentationMode.wrappedValue.dismiss() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.iappayTextPrimary)
                    }

                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.iappayCard)
                            .frame(width: 32, height: 32)
                        Image(systemName: appIconSystem)
                            .font(.system(size: 16))
                            .foregroundColor(.iappayPurple)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(appName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.iappayTextPrimary)
                            .lineLimit(1)
                        Text(bundleId)
                            .font(.system(size: 10))
                            .foregroundColor(.iappayTextSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    Button(action: { showingLogs = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "list.bullet.rectangle")
                                .font(.system(size: 12))
                            Text("Logs")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundColor(.iappayPurple)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.iappayCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.iappayBorder, lineWidth: 1))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 14)

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        // 3 Metrics Box matching video
                        HStack(spacing: 0) {
                            VStack(spacing: 4) {
                                Text("\(allCount)")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.iappayTextPrimary)
                                Text("GÓI IAP")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)

                            Divider().frame(height: 32).background(Color.iappayBorder)

                            VStack(spacing: 4) {
                                Text("\(trialCount)")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.iappayGreen)
                                Text("DÙNG THỬ")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)

                            Divider().frame(height: 32).background(Color.iappayBorder)

                            VStack(spacing: 4) {
                                Text("\(discountCount)")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(.iappayOrange)
                                Text("GIẢM GIÁ")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.iappayTextSecondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .padding(.vertical, 14)
                        .background(Color.iappayCard)
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.iappayBorder, lineWidth: 1))

                        // Filter Pills & Sort Popover Menu
                        HStack(spacing: 8) {
                            FilterPill(title: "Tất cả (\(allCount))", isSelected: selectedFilter == "Tất cả") {
                                selectedFilter = "Tất cả"
                            }
                            FilterPill(title: "Dùng thử (\(trialCount))", isSelected: selectedFilter == "Dùng thử") {
                                selectedFilter = "Dùng thử"
                            }
                            FilterPill(title: "Giảm giá (\(discountCount))", isSelected: selectedFilter == "Giảm giá") {
                                selectedFilter = "Giảm giá"
                            }

                            Spacer()

                            Menu {
                                Section(header: Text("SẮP XẾP")) {
                                    ForEach(IAPSortOption.allCases) { opt in
                                        Button(action: { selectedSort = opt }) {
                                            HStack {
                                                Text(opt.rawValue)
                                                if selectedSort == opt { Image(systemName: "checkmark") }
                                            }
                                        }
                                    }
                                }

                                Section(header: Text("HIỂN THỊ")) {
                                    ForEach(IAPFilterDisplay.allCases) { disp in
                                        Button(action: { selectedDisplay = disp }) {
                                            HStack {
                                                Text(disp.rawValue)
                                                if selectedDisplay == disp { Image(systemName: "checkmark") }
                                            }
                                        }
                                    }
                                }
                            } label: {
                                Image(systemName: "line.3.horizontal.decrease.circle")
                                    .font(.system(size: 18))
                                    .foregroundColor(.iappayPurple)
                                    .padding(8)
                                    .background(Color.iappayCard)
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color.iappayBorder, lineWidth: 1))
                            }
                        }

                        // Search Bar
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.iappayTextMuted)
                            TextField("Tìm gói, giá (ví dụ: 230 hoặc pro)...", text: $searchText)
                                .foregroundColor(.iappayTextPrimary)
                            if !searchText.isEmpty {
                                Button(action: { searchText = "" }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.iappayTextMuted)
                                }
                            }
                        }
                        .padding(10)
                        .background(Color.iappayCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.iappayBorder, lineWidth: 1))

                        // Product List grouped
                        if processedItems.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "tray")
                                    .font(.system(size: 36))
                                    .foregroundColor(.iappayTextMuted)
                                    .padding(.top, 24)
                                Text("Không có gói nào khớp bộ lọc")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.iappayTextPrimary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 30)
                        } else {
                            ForEach(groupedProcessedItems.keys.sorted(), id: \.self) { groupKey in
                                VStack(alignment: .leading, spacing: 10) {
                                    Text(groupKey.uppercased())
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.iappayTextMuted)
                                        .padding(.top, 6)

                                    ForEach(groupedProcessedItems[groupKey] ?? []) { item in
                                        AppIAPRow(
                                            item: item,
                                            onTapRow: { selectedItemForSpec = item },
                                            onTapBuy: { selectedItemForPurchase = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .navigationBarHidden(true)
        .sheet(item: $selectedItemForSpec) { item in
            IAPSpecSheet(item: item, isPresented: Binding(
                get: { selectedItemForSpec != nil },
                set: { if !$0 { selectedItemForSpec = nil } }
            ), onBuyTapped: {
                selectedItemForPurchase = item
            })
        }
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

// AppIAPRow with direct Spec tap and Buy button
struct AppIAPRow: View {
    let item: IAPItem
    let onTapRow: () -> Void
    let onTapBuy: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            // Main info - tap opens Spec Sheet
            Button(action: onTapRow) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.iappayCard)
                            .frame(width: 42, height: 42)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.iappayBorder, lineWidth: 1))
                        Image(systemName: item.appIconSystem)
                            .font(.system(size: 18))
                            .foregroundColor(.iappayPurple)
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(item.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.iappayTextPrimary)
                                .lineLimit(1)

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
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            // Price Button - tap triggers Purchase Sheet
            Button(action: onTapBuy) {
                Text(item.isTrial ? "Dùng thử" : item.formattedPrice)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(item.isTrial ? .white : (item.isFree ? .black : .white))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(item.isTrial ? Color.iappayGreen : (item.isFree ? Color.iappayYellow : Color.iappayPurple))
                    .cornerRadius(12)
            }
        }
        .padding(12)
        .background(Color.iappayCard)
        .cornerRadius(14)
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.iappayBorder, lineWidth: 1))
    }
}
