import SwiftUI

public struct SettingsView: View {
    @AppStorage("defaultPaymentMode") private var defaultMode: String = "App Store"
    @AppStorage("appStoreCountry") private var appStoreCountry: String = "VN"
    @AppStorage("noCacheMode") private var noCacheMode: Bool = false
    @AppStorage("autoInjectReceipt") private var autoInjectReceipt: Bool = true

    @State private var showingAlert = false
    @State private var alertMessage = ""

    private let countries = ["VN", "US", "JP", "KR", "GB", "SG"]

    public var body: some View {
        NavigationView {
            ZStack {
                Color.iappayBackground.edgesIgnoringSafeArea(.all)

                Form {
                    Section(header: Text("CỬA HÀNG & QUỐC GIA").foregroundColor(.iappayTextMuted)) {
                        Picker("Quốc gia App Store", selection: $appStoreCountry) {
                            ForEach(countries, id: \.self) { c in
                                Text(c).tag(c)
                            }
                        }
                        .foregroundColor(.iappayTextPrimary)
                    }
                    .listRowBackground(Color.iappayCard)

                    Section(header: Text("PHƯƠNG THỨC THANH TOÁN").foregroundColor(.iappayTextMuted)) {
                        Picker("Chế độ mua", selection: $defaultMode) {
                            Text("App Store (Chính thức - Có popup StoreKit)").tag("App Store")
                            Text("Apple Sandbox (Môi trường thử nghiệm)").tag("Sandbox")
                            Text("TestFlight / Direct (Receipt Inject, không popup)").tag("Direct")
                        }
                        .foregroundColor(.iappayTextPrimary)

                        Toggle("Tự động inject receipt vào app đích", isOn: $autoInjectReceipt)
                            .foregroundColor(.iappayTextPrimary)
                    }
                    .listRowBackground(Color.iappayCard)

                    Section(header: Text("DỮ LIỆU & BỘ NHỚ TẠM").foregroundColor(.iappayTextMuted)) {
                        Toggle("No-Cache Mode (Luôn làm mới dữ liệu)", isOn: $noCacheMode)
                            .foregroundColor(.iappayTextPrimary)

                        Button(action: {
                            restartStoreDaemons()
                        }) {
                            HStack {
                                Image(systemName: "arrow.clockwise.circle.fill")
                                    .foregroundColor(.iappayPurple)
                                Text("Khởi động lại Store Daemons (storekitd, itunesstored)")
                                    .foregroundColor(.iappayTextPrimary)
                            }
                        }

                        Button(action: {
                            clearAllData()
                        }) {
                            HStack {
                                Image(systemName: "trash.fill")
                                    .foregroundColor(.iappayRed)
                                Text("Xoá sạch toàn bộ data & reset")
                                    .foregroundColor(.iappayRed)
                            }
                        }

                        Button(action: {
                            StoreKitService.shared.logs.removeAll()
                            showAlert(msg: "Đã xoá sạch lịch sử logs.")
                        }) {
                            Text("Xoá toàn bộ Logs")
                                .foregroundColor(.iappayTextSecondary)
                        }
                    }
                    .listRowBackground(Color.iappayCard)

                    Section(header: Text("THÔNG TIN PHÁT TRIỂN").foregroundColor(.iappayTextMuted)) {
                        HStack {
                            Text("Phiên bản")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            Text("Dini Pay v1.0.0 (build 2026)")
                                .foregroundColor(.iappayTextSecondary)
                        }

                        HStack {
                            Text("Môi trường")
                                .foregroundColor(.iappayTextPrimary)
                            Spacer()
                            Text("TrollStore / Rootless Jailbreak")
                                .foregroundColor(.iappayGreen)
                        }
                    }
                    .listRowBackground(Color.iappayCard)
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Cài Đặt")
            .navigationBarTitleDisplayMode(.inline)
            .alert(isPresented: $showingAlert) {
                Alert(title: Text("Thông Báo"), message: Text(alertMessage), dismissButton: .default(Text("OK")))
            }
        }
    }

    private func showAlert(msg: String) {
        alertMessage = msg
        showingAlert = true
    }

    private func restartStoreDaemons() {
        // Kill storekitd and itunesstored dynamically via dlsym to bypass iOS SDK compile restriction
        typealias SystemFunction = @convention(c) (UnsafePointer<CChar>) -> Int32
        if let handle = dlopen(nil, RTLD_NOW),
           let sym = dlsym(handle, "system") {
            let sysFunc = unsafeBitCast(sym, to: SystemFunction.self)
            for p in ["storekitd", "itunesstored"] {
                _ = sysFunc("killall -9 \(p) 2>/dev/null")
            }
        }

        // Also post Darwin notification to notify daemon listeners
        let notifyName = CFNotificationName("com.adr.checkiap.restart_daemons" as CFString)
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), notifyName, nil, nil, true)

        StoreKitService.shared.addLog(message: "Đã gửi lệnh khởi động lại Store Daemons (storekitd, itunesstored)")
        showAlert(msg: "Đã khởi động lại storekitd và itunesstored thành công.")
    }

    private func clearAllData() {
        let fileManager = FileManager.default
        let searchPaths = [
            "/var/mobile/Documents/IAPCheck",
            "/tmp/IAPCheck"
        ]

        for p in searchPaths {
            if fileManager.fileExists(atPath: p) {
                try? fileManager.removeItem(atPath: p)
            }
        }

        StoreKitService.shared.items.removeAll()
        StoreKitService.shared.logs.removeAll()
        showAlert(msg: "Đã dọn dẹp sạch toàn bộ cache và snapshot IAP trên máy.")
    }
}
