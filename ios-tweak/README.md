# IAPCheck iOS — StoreKit Interceptor & Inspector Tweak

Tweak Jailbreak (Theos / Logos) dành cho iOS, tương đương với module **Xposed / LSPosed** của bản Android (`IapHookModule.kt`).

Tweak này tự động inject vào runtime của các ứng dụng mục tiêu trên iOS, hook vào framework **StoreKit** để:
1. **Bắt toàn bộ Product ID** mà ứng dụng gửi lên Apple StoreKit (`SKProductsRequest`).
2. **Trích xuất thông tin chi tiết**: Giá niêm yết, chu kỳ thuê bao (`P1W`, `P1M`, `P1Y`).
3. **Phát hiện Gói Dùng Thử Ẩn (Free Trial)** & **Mã Giảm Giá (Introductory Discount)** mà giao diện của app có thể che giấu.
4. **Hiển thị In-App HUD (Floating Button & Inspector Modal)** trực tiếp trên màn hình app để xem và xuất dữ liệu JSON ngay lập tức.
5. **Ghi log & Lưu JSON tự động** tại `/var/mobile/Documents/IAPCheck/<bundle_id>_iap.json`.

---

## 📁 Cấu Trúc Dự Án

```
ios-tweak/
├── Makefile                     # Cấu hình Theos build (hỗ trợ Rootless & Rootful)
├── control                      # Thông tin gói Debian (.deb)
├── IAPCheck.plist               # Filter inject (UIKit Apps)
├── Tweak.x                      # Core Logos Hooks (StoreKit & UIWindow)
├── IAPProductModel.h / .m       # Data Models & JSON Exporter (chuẩn hoá theo Android)
├── IAPOverlayViewController.h/.m# Floating HUD & Inspector Modal
└── README.md                    # Hướng dẫn biên dịch & sử dụng
```

---

## 🛠 Hướng Dẫn Biên Dịch (Theos)

### 1. Yêu Cầu Môi Trường
- Máy tính macOS hoặc Linux / WSL có cài đặt **Theos**.
- iOS SDK 15.0+ trong thư mục `$THEOS/sdks`.

### 2. Biên Dịch Gói `.deb`

* **Cho Jailbreak Rootless (Dopamine, Palera1n iOS 15 - 16+):**
```bash
cd ios-tweak
export THEOS_PACKAGE_SCHEME=rootless
make package FINALPACKAGE=1
```
*File đầu ra:* `packages/com.adr.checkiap.tweak_<version>_iphoneos-arm64.deb`

* **Cho Jailbreak Rootful (Cũ):**
```bash
export THEOS_PACKAGE_SCHEME=rootful
make package FINALPACKAGE=1
```

* **Cài trực tiếp qua SSH vào iPhone:**
```bash
export THEOS_DEVICE_IP=192.168.1.xxx
export THEOS_DEVICE_PORT=22
make do
```

---

## 📲 Cách Sử Dụng Trên Thiết Bị

1. Cài đặt file `.deb` bằng **Sileo**, **Zebra** hoặc **Filza File Manager**.
2. Mở bất kỳ ứng dụng nào bạn muốn kiểm tra gói IAP (ví dụ app subscription, game,...).
3. Sau khi app khởi động:
   * **Nút nổi "IAP"** màu xanh neon sẽ xuất hiện ở góc phải màn hình, hiển thị số lượng gói IAP vừa bắt được.
   * Kéo nút nổi để di chuyển bất kỳ vị trí nào trên màn hình.
   * Nhấn vào nút nổi (hoặc **lắc thiết bị / Shake**) để mở cửa sổ **IAP Check Inspector**.
4. Trong cửa sổ Inspector:
   * Xem danh sách các gói subscription, gói vĩnh viễn.
   * Các gói có trial ẩn sẽ được gắn nhãn sáng màu xanh: `[★ FREE TRIAL DETECTED]`.
   * Nhấn **📋 Copy JSON** để copy cấu trúc dữ liệu JSON vào Clipboard phục vụ phân tích.
   * File snapshot cũng được tự động lưu tại thư mục Documents của app (`IAPCheck/<bundle_id>_iap.json`).
