# IAPPay iOS (.ipa / TrollStore)

Ứng dụng iOS độc lập viết bằng **SwiftUI**, thiết kế chuẩn 1:1 theo video **`IMG_0315.MOV`**. Hỗ trợ đóng gói thành file **`.ipa`** và **`.tipa`** cài đặt trực tiếp qua **TrollStore** hoặc các công cụ sideload (Sideloadly, AltStore, Esign).

---

## 📱 Giao Diện & Tính Năng (1:1 Theo Video)

### 1. Tab "Đã cài đặt" (Màn hình chính)
* **Khối thống kê 3 cột:**
  * **GÓI IAP:** Tổng số gói đã phát hiện (ví dụ: `18`).
  * **DÙNG THỬ:** Số lượng gói Free Trial (màu xanh lá `#30D158`, ví dụ: `3`).
  * **GIẢM GIÁ:** Số lượng gói ưu đãi / khuyến mãi (màu vàng `#FF9F0A`, ví dụ: `0`).
* **Thanh lọc dạng Pill:**
  * `Tất cả (18)` (viên thuốc màu tím)
  * `Dùng thử (3)`
  * `Giảm giá`
* **Thanh tìm kiếm:** Tìm nhanh theo tên gói, app, hoặc mô tả.
* **Nhóm ứng dụng:** Hiển thị theo từng app (ví dụ: `YOUTUBE PREMIUM • 7 gói` có icon YouTube).
* **Card sản phẩm:**
  * Tiêu đề: `Premium Lite`, `YouTube Music`, `YouTube Premium`.
  * Nhãn: `[DÙNG THỬ 30 NGÀY]` (xanh lá), `[ẨN]` (xám đậm - gói ẩn).
  * Mô tả: `Dùng thử 30 ngày • 1 tháng • sau đó 65.000đ • Hidden`.
  * Nút giá: `Miễn phí` (viên thuốc màu vàng chữ đen) hoặc `65.000đ` (màu tím).
  * Ngôi sao đánh dấu yêu thích.

### 2. Sheet "Xác nhận mua" (Modal)
* Chạm vào bất kỳ gói nào (như `Premium Lite - Miễn phí`) sẽ mở sheet xác nhận mua:
  * Thông tin app và gói IAP.
  * **3 Chế độ thanh toán:**
    1. **`App Store`:** Hiện sheet thanh toán chính thức của Apple StoreKit (trừ tiền thật / kích hoạt dùng thử thật).
    2. **`Sandbox`:** Thử nghiệm, không trừ tiền, dùng tài khoản Apple Sandbox.
    3. **`Direct`:** Inject receipt giả lập vào container của app đích ngay lập tức, không popup.
  * Nút lớn: **`Mua Miễn phí`** / **`Mua [Giá]`** màu tím.
  * Hiệu ứng chờ: **`Đang xử lý giao dịch...`** kèm vòng quay spinner.
  * Xử lý lỗi Apple: Hộp thoại báo lỗi chuẩn `[AMSErrorDomain 301] The response has an invalid status code` kèm nút **`Copy chi tiết`**.

### 3. 4 Tabs Điều Hướng
1. **Khám phá (`sparkles`):** Tổng hợp các gói Free Trial & Deal ẩn hot.
2. **Đã cài đặt (`square.grid.2x2`):** Quản lý và kích hoạt IAP của các app trên máy.
3. **Thư viện (`folder.fill`):** Lưu trữ các gói IAP đã đánh dấu sao.
4. **Cài đặt (`gearshape.fill`):** Tuỳ chỉnh chế độ thanh toán mặc định, cấu hình inject receipt, kiểm tra trạng thái Tweak và xoá logs.

### 4. Hệ Thống "Logs" (Nút góc phải trên cùng)
* Ghi lại toàn bộ lịch sử gửi lệnh StoreKit, request IPC và phản hồi từ máy chủ Apple với dấu thời gian chính xác từng mili-giây.

---

## 🛠 Lệnh Đóng Gói File .IPA

Chạy script tự động:
```bash
cd ios-app
chmod +x build_ipa.sh
./build_ipa.sh
```

Kết quả xuất ra tại thư mục `output/`:
* **`output/IAPPay.ipa`**: Cài qua Sideloadly, AltStore, Esign, Scarlet.
* **`output/IAPPay.tipa`**: Cài trực tiếp qua **TrollStore** (không bao giờ bị thu hồi chứng chỉ, tự do inject receipt và bypass sandbox).
