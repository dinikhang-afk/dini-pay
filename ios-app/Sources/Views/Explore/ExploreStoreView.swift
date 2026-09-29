import SwiftUI

public struct ExploreStoreView: View {
    @ObservedObject var store = StoreKitService.shared
    @State private var selectedItem: IAPItem? = nil

    public var featuredTrials: [IAPItem] {
        store.items.filter { $0.isTrial }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // Header
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Khám Phá")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.iappayTextPrimary)
                            Text("Tổng hợp các gói Free Trial & Ưu đãi ẩn hot nhất")
                                .font(.system(size: 13))
                                .foregroundColor(.iappayTextSecondary)
                        }
                        .padding(.top, 8)

                        if let topTrial = featuredTrials.first {
                            // Dynamic Banner Card for top discovered trial
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
                                    .font(.system(size: 20, weight: .heavy))
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

                        // Featured Free Trials or Empty State
                        if featuredTrials.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 40))
                                    .foregroundColor(.iappayTextMuted)
                                    .padding(.top, 20)
                                Text("Chưa có gói dùng thử")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.iappayTextPrimary)
                                Text("Mở các ứng dụng trên thiết bị (có cài tweak IAPCheck) để thu thập các gói IAP thật.")
                                    .font(.system(size: 12))
                                    .foregroundColor(.iappayTextSecondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 24)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 30)
                        } else {
                            Text("GÓI DÙNG THỬ NỔI BẬT")
                                .font(.system(size: 12, weight: .bold))
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
        }
    }
}
