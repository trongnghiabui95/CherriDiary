# TikTok Live → Cherri Diary

Worker Node 22 dùng `tiktok-live-connector` 2.1.0 để nhận sự kiện CHAT từ TikTok, chuyển nội dung/người gửi/message ID
qua REST có JWT tới backend. Backend phát sự kiện vào WebSocket mà Android đã kết nối.
Không cần chọn từng comment trên điện thoại. Không gửi comment hoặc thực hiện thao tác trên tài khoản TikTok.

## Chạy bằng Git Bash + Docker

1. Tạo phiên trong tab **Live** Android; ghi lại **ID phiên Cherri Diary**. Backend Docker phải đang chạy.
2. Tại thư mục gốc project chạy `bash scripts/Deploy-TikTok-Connector.sh --prepare`.
3. Mở `D:\Project Cherri\Compose Deploy App\.env.connector`, điền:

   ```dotenv
   TIKTOK_USERNAME=ten_shop_tiktok
   CHERRI_LIVE_SESSION_ID=1
   CHERRI_USERNAME=admin
   CHERRI_PASSWORD='mat_khau_Cherri_Diary'
   EULER_API_KEY=
   ```

   Username là phần sau @ trong URL `https://www.tiktok.com/@ten_shop_tiktok/live`, không phải tên hiển thị/Room ID.
   Mật khẩu là tài khoản Cherri Diary đã có, không phải mật khẩu TikTok. Nên dùng tài khoản staff dành cho connector.
4. Bắt đầu phát livestream TikTok. Trong Android, nhập đúng ID Cherri ở bước 1 và bấm **Kết Nối Comment Gateway**.
5. Chạy `bash scripts/Deploy-TikTok-Connector.sh`. Có thể đóng terminal.
6. Xem `docker logs --tail 100 cherridiary_tiktok_connector`. Khi nhận được `TikTok connected`, comment mới sẽ
   tự đổ vào tab Live; bấm comment để kiểm tra/chốt đơn. Log hiển thị Room ID thật khi kết nối thành công.

Connector reconnect mỗi 60 giây khi streamer offline hoặc bị ngắt; gửi API có timeout/retry exponential backoff,
tự đăng nhập lại khi JWT hết hạn, dùng cùng eventId khi retry để gateway loại trùng trong cửa sổ 5 phút.
Giữ tối đa 2.000 comment chờ trong RAM khi backend mất kết nối; không giữ hàng đợi qua restart và không lưu lịch sử comment.
Android chỉ nhận comment mới khi đang kết nối, hiện hiển thị tối đa 20 comment gần nhất.
Sau khi kết thúc phiên Cherri, cập nhật ID mới trong `.env.connector` và chạy lại script cho phiên tiếp theo.

## Giới hạn kết nối thực tế

Đây là thư viện không chính thức, phụ thuộc TikTok và Euler Stream sign server:
https://github.com/zerodytrash/TikTok-Live-Connector . Nếu lỗi signing/giới hạn dịch vụ, cấu hình `EULER_API_KEY`
bằng key của bạn tại https://www.eulerstream.com rồi deploy lại. Không có key nào được cấp tự động.
Chỉ có thể xác nhận comment TikTok thật khi có username đang LIVE và dịch vụ signing chấp nhận kết nối.
Không cam kết lấy đầy đủ comment bị TikTok lọc, comment cũ hoặc comment trong thời gian ngắt kết nối.

## Kiểm tra code

```bash
docker run --rm -v "$(pwd)/tiktok-connector:/app" -w /app node:22-bookworm-slim npm test
```

Test xác minh ánh xạ comment, JWT hết hạn/retry cùng event ID và lỗi phiên đã kết thúc.
