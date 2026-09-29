import SwiftUI

public struct SettingsView: View {
    @AppStorage("defaultPaymentMode") private var defaultMode: String = "App Store"
    @AppStorage("autoInjectReceipt") private var autoInjectReceipt: Bool = true
    @State private var showingClearAlert = false

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                Form {
                    Section(header: Text("CHẾ ĐỘ THANH TOÁN MẶC ĐỊNH").foregroundColor(.iappayTextMuted)) {
                        Picker("Chế độ", selection: $defaultMode) {
                            Text("App Store (Chính thức)").tag("App Store")
                            Text("Sandbox (Thử nghiệm)").tag("Sandbox")
                            Text("Direct (Receipt Inject)").tag("Direct")
                        }
                        .foregroundColor(.iappayTextPrimary)

                        Toggle("Tự động inject receipt vào app đích", isOn: $autoInjectReceipt)
                            .foregroundColor(.iappayTextPrimary)
                    }
                    .listRowBackground(Color.iappayCard)

                    Section(header: Text("TWEAK & HỆ THỐNG").foregroundColor(.iappayTextMuted)) {
                        HStack {
                            Text("Trạng thái Tweak")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            HStack(spacing: 6) {
                                Circle().fill(Color.iappayGreen).frame(width: 8, height: 8)
                                Text("Đã kích hoạt")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.iappayGreen)
                            }
                        }

                        HStack {
                            Text("Môi trường thực thi")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            Text("TrollStore / Rootless")
                                .font(.system(size: 13))
                                .foregroundColor(.iappayTextSecondary)
                        }

                        Button(action: {
                            StoreKitService.shared.logs.removeAll()
                            showingClearAlert = true
                        }) {
                            Text("Xoá toàn bộ Logs")
                                .foregroundColor(.iappayRed)
                        }
                    }
                    .listRowBackground(Color.iappayCard)

                    Section(header: Text("THÔNG TIN ỨNG DỤNG").foregroundColor(.iappayTextMuted)) {
                        HStack {
                            Text("Phiên bản")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            Text("Dini Pay v1.0.0")
                                .foregroundColor(.iappayTextSecondary)
                        }

                        HStack {
                            Text("Tác giả")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            Text("Dini Team")
                                .foregroundColor(.iappayTextSecondary)
                        }
                    }
                    .listRowBackground(Color.iappayCard)
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Cài Đặt")
            .navigationBarTitleDisplayMode(.inline)
            .alert(isPresented: $showingClearAlert) {
                Alert(title: Text("Thành công"), message: Text("Đã dọn dẹp sạch nhật ký giao dịch."), dismissButton: .default(Text("OK")))
            }
        }
    }
}
