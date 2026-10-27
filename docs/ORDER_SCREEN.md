# Màn hình đơn hàng

`OrdersScreen.kt` dùng Compose Material 3: một thanh tìm kiếm, nút lọc hình phễu, bottom sheet chọn trạng thái và ngày, hai tab Theo Đơn/Gom Theo Khách, danh sách card và phân trang.

Card hiển thị tên khách, TikTok ID hoặc SĐT, trạng thái tiếng Việt, tên/mã sản phẩm và số lượng, tổng tiền hàng cộng phí giao, thời gian tạo đơn theo múi giờ thiết bị. Nháp có badge xám, chờ đi đơn màu cam, đơn CONFIRMED có mã vận đơn có badge xanh “Đã đóng gói”. Backend chưa có trạng thái PACKED riêng; badge này thể hiện bước gom và gán vận đơn đã hoàn tất.

## API

- `GET /api/v1/orders`: thêm `q`, `from`, `to`; từ khóa tìm OR trên tên, SĐT, TikTok ID và mã đơn. Ký tự `%`, `_` được tìm như ký tự thật. `from` bao gồm, `to` không bao gồm; Android chuyển ngày kết thúc thành đầu ngày kế tiếp theo múi giờ thiết bị.
- `GET /api/v1/orders/grouped`: cùng bộ lọc, phân trang theo nhóm khách. Chỉ xét DRAFT/CONFIRMED. Bộ lọc chọn khách phù hợp; mỗi nhóm chứa **toàn bộ đơn chưa giao** của khách đó, kể cả đơn ngoài khoảng ngày hoặc không khớp mã tìm kiếm, để tránh sót hàng trong kiện. UI giải thích điều này. Khách chưa có cả SĐT lẫn TikTok ID không được gom với đơn khác dựa trên tên.
- `POST /api/v1/orders/pack`: `{ "orderIds": [1, 2], "trackingCode": "GH123" }`. Lưu một mã chung trong giao dịch, xác nhận các đơn nháp đã có sản phẩm. Không trừ kho lại, không xóa hay thay thế đơn gốc, không thay đổi giá/cọc/phí giao. Giữ lịch sử và thao tác trên từng đơn gốc.

Các định danh khách đã được backend chuẩn hóa và kiểm tra duy nhất khi tạo khách/đơn; gom theo customerId tương ứng SĐT/TikTok ID. API gom không nhận tên khách làm bằng chứng đồng nhất.

## Vận đơn

Chưa có thông tin hãng/API vận chuyển. CTA mở hộp thoại nhập mã đã tạo ở hãng, sau đó đóng gói các đơn chung mã. Đây chưa phải thao tác đặt vận chuyển tự động trên GHN/GHTK. Đơn phải chưa giao, có sản phẩm và cùng khách. API từ chối kiện đã dùng, mã đã thuộc đơn khác, đơn đã giao, đơn trống hoặc mã khác đã gán. Tối đa 100 đơn/lần; khóa đơn theo thứ tự và khóa mã trong PostgreSQL chống thao tác trùng đồng thời. Gửi lại cùng danh sách và mã là an toàn.

Test tích hợp kiểm tra tìm kiếm, ngày, phân trang nhóm, loại đơn đã giao, lưu mã chung, retry, bảo toàn tồn kho, rollback khi khác khách/đơn nháp trống, và hợp đồng HTTP.
