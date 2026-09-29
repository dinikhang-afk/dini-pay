import SwiftUI

public struct LibraryView: View {
    @ObservedObject var store = StoreKitService.shared
    @State private var selectedItem: IAPItem? = nil

    public var starredItems: [IAPItem] {
        store.items.filter { $0.isStarred }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                if starredItems.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "folder")
                            .font(.system(size: 44))
                            .foregroundColor(.iappayTextMuted)
                        Text("Thư viện trống")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.iappayTextPrimary)
                        Text("Nhấn vào biểu tượng ngôi sao bên cạnh bất kỳ gói IAP nào để lưu vào thư viện.")
                            .font(.system(size: 12))
                            .foregroundColor(.iappayTextSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("GÓI ĐÃ LƯU (\(starredItems.count))")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.iappayTextMuted)
                                .padding(.top, 8)

                            ForEach(starredItems) { item in
                                IAPItemRow(item: item) {
                                    selectedItem = item
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
            }
            .navigationTitle("Thư Viện")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: $selectedItem) { item in
                PurchaseConfirmSheet(item: item, isPresented: Binding(
                    get: { selectedItem != nil },
                    set: { if !$0 { selectedItem = nil } }
                ))
            }
        }
    }
}
