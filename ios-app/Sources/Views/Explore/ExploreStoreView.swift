import SwiftUI

public struct ExploreStoreView: View {
    @ObservedObject var store = StoreKitService.shared
    @ObservedObject var searchService = AppStoreSearchService.shared

    @State private var queryText: String = ""
    @State private var selectedCategory: String = "Tất cả"
    @State private var selectedItem: IAPItem? = nil
    @State private var selectedOnlineApp: OnlineAppSearchResult? = nil

    private let categories = ["Tất cả", "AI & Trợ lý", "Video & Ảnh", "Học tập", "Mạng xã hội", "Tiện ích"]

    public var featuredTrials: [IAPItem] {
        store.items.filter { $0.isTrial }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        // Title
                        Text("Khám Phá")
                            .font(.system(size: 26, weight: .bold))
                            .foregroundColor(.iappayTextPrimary)
                            .padding(.top, 8)

                        // Search Bar matching video IMG_3174
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.iappayTextMuted)
                            TextField("Tên app, Bundle ID hoặc link App Store...", text: $queryText)
                                .foregroundColor(.iappayTextPrimary)
                                .onSubmit {
                                    searchService.searchAppStore(query: queryText)
                                }
                            if !queryText.isEmpty {
                                Button(action: {
                                    queryText = ""
                                    searchService.searchResults = []
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.iappayTextMuted)
                                }
                            }
                        }
                        .padding(12)
                        .background(Color.iappayCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.iappayBorder, lineWidth: 1))

                        // Category Pills matching video
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(categories, id: \.self) { cat in
                                    FilterPill(title: cat, isSelected: selectedCategory == cat) {
                                        selectedCategory = cat
                                        if cat != "Tất cả" {
                                            searchService.searchAppStore(query: cat)
                                        }
                                    }
                                }
                            }
                        }

                        // Online Search Results if user searched
                        if searchService.isSearching {
                            HStack {
                                Spacer()
                                ProgressView("Đang tìm kiếm trên App Store...")
                                    .foregroundColor(.iappayTextSecondary)
                                Spacer()
                            }
                            .padding(.vertical, 20)
                        } else if !searchService.searchResults.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("KẾT QUẢ TÌM KIẾM APP STORE (\(searchService.searchResults.count))")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.iappayTextMuted)

                                ForEach(searchService.searchResults) { app in
                                    Button(action: {
                                        selectedOnlineApp = app
                                    }) {
                                        HStack(spacing: 12) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 10)
                                                    .fill(Color.iappayCard)
                                                    .frame(width: 44, height: 44)
                                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.iappayBorder, lineWidth: 1))
                                                Image(systemName: "arrow.down.app.fill")
                                                    .font(.system(size: 20))
                                                    .foregroundColor(.iappayPurple)
                                            }

                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(app.appName)
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundColor(.iappayTextPrimary)
                                                    .lineLimit(1)
                                                Text(app.bundleId)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.iappayTextSecondary)
                                                    .lineLimit(1)
                                            }

                                            Spacer()

                                            HStack(spacing: 4) {
                                                Text("Xem gói")
                                                    .font(.system(size: 12, weight: .bold))
                                                Image(systemName: "chevron.right")
                                                    .font(.system(size: 10))
                                            }
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(Color.iappayPurple)
                                            .cornerRadius(12)
                                        }
                                        .padding(12)
                                        .background(Color.iappayCard)
                                        .cornerRadius(14)
                                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.iappayBorder, lineWidth: 1))
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                }
                            }
                        }

                        // Hot Trial Banner
                        if let topTrial = featuredTrials.first {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("HOT TRIAL")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.iappayGreen)
                                        .cornerRadius(6)

                                    Spacer()

                                    if let badge = topTrial.trialBadge {
                                        Text(badge)
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(.iappayYellow)
                                    }
                                }

                                Text(topTrial.title)
                                    .font(.system(size: 18, weight: .heavy))
                                    .foregroundColor(.white)

                                Text(topTrial.subtitle)
                                    .font(.system(size: 12))
                                    .foregroundColor(.iappayTextSecondary)

                                Button(action: {
                                    selectedItem = topTrial
                                }) {
                                    Text("Kích Hoạt Ngay")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 8)
                                        .background(Color.iappayPurple)
                                        .cornerRadius(8)
                                }
                                .padding(.top, 4)
                            }
                            .padding(16)
                            .background(
                                LinearGradient(
                                    colors: [Color(hex: 0x2A1B4E), Color.iappayCard],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .cornerRadius(16)
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.iappayPurple.opacity(0.4), lineWidth: 1))
                        }

                        // Featured Free Trials
                        if featuredTrials.isEmpty && searchService.searchResults.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 36))
                                    .foregroundColor(.iappayTextMuted)
                                    .padding(.top, 16)
                                Text("Chưa có gói dùng thử")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.iappayTextPrimary)
                                Text("Mở các ứng dụng trên thiết bị (hoặc tìm kiếm ứng dụng trên App Store ở trên) để xem các gói IAP.")
                                    .font(.system(size: 12))
                                    .foregroundColor(.iappayTextSecondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 24)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                        } else if !featuredTrials.isEmpty {
                            Text("GÓI DÙNG THỬ NỔI BẬT")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.iappayTextMuted)

                            ForEach(featuredTrials) { item in
                                IAPItemRow(item: item) {
                                    selectedItem = item
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
            .navigationBarHidden(true)
            .sheet(item: $selectedItem) { item in
                PurchaseConfirmSheet(item: item, isPresented: Binding(
                    get: { selectedItem != nil },
                    set: { if !$0 { selectedItem = nil } }
                ))
            }
            .sheet(item: $selectedOnlineApp) { app in
                AppIAPDetailView(
                    appName: app.appName,
                    bundleId: app.bundleId,
                    appIconSystem: "arrow.down.app.fill",
                    initialItems: store.items.filter { $0.appBundleId == app.bundleId }
                )
            }
        }
    }
}
