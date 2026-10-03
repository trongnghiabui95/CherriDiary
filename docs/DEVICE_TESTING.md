# Kiểm tra Android trên thiết bị thật

Build/lint kiểm tra code và manifest; các thao tác TikTok cần kiểm tra trên điện thoại đã cài TikTok.
Các bước dưới đây là checklist cần thực hiện, không phải kết quả đã xác nhận trên thiết bị.

1. Đăng nhập vào backend với admin/staff; kiểm tra token sai/hết hạn yêu cầu login lại.
2. Xem/tìm đơn và kiểm tra thanh toán, vận đơn, chuyển trạng thái. Staff không tạo được user hoặc sửa sản phẩm bằng API.
3. Cấp/từ chối quyền overlay; kéo nút; tắt qua app và notification; bảo đảm không còn service chụp sau khi tắt.
4. Bật Accessibility, chọn comment `0987654321 A1 2` hoặc `@nick A1`, bấm nút nổi; đối chiếu text và ảnh thực tế.
5. Thử khi TikTok không có text node: form vẫn mở, cho nhập thủ công; ảnh toàn màn hình phải hiển thị để kiểm tra.
6. Cấp/từ chối MediaProjection. Android 14/15: cấp quyền phiên đầu, chụp nhiều đơn trong cùng phiên, dừng bằng system screen-sharing control rồi bấm lại để xin quyền mới.
7. Xoay màn hình trong phiên chụp; kiểm tra bounds/ảnh; thử màn hình được bảo vệ và lỗi timeout.
8. Kiểm tra blacklist, cọc > tiền hàng+ship, hết kho, nhiều mã hàng, thông tin khách bị xung đột.
9. Gửi đơn có PNG/JPEG từ thư viện; từ chối file lớn hơn 5 MB hoặc định dạng không hỗ trợ.
10. Ngắt mạng sau khi bấm submit, gửi lại từ chính form đó; đối chiếu chỉ có một đơn và chỉ trừ kho một lần.
11. Hủy cùng đơn hai lần, kiểm tra chỉ hoàn kho một lần. Hoàn tất chỉ sau khi đã ghi nhận đủ tiền.
12. Kết nối gateway đúng liveSessionId, gửi event qua REST, bấm comment để mở form; tài khoản bị khóa phải mất kết nối tối đa một phút.

TikTok/OEM có thể cung cấp Accessibility tree khác nhau; ghi lại phiên bản Android, TikTok, model máy và ảnh kết quả khi kiểm tra.
