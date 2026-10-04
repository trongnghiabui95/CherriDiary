# REST API và WebSocket

Base URL `http://localhost:8080/api/v1`. Ngoại trừ login, mọi endpoint cần `Authorization: Bearer <accessToken>`.
JSON dùng camelCase. Ngày giờ ISO-8601 UTC, enum là chuỗi in hoa, tiền là JSON number được xử lý bằng BigDecimal.
Lỗi có `status`, `message`; đa số lỗi còn có `timestamp`. Trang bắt đầu từ 0; size 1..100.

| Method | Endpoint | Chức năng / quyền |
| --- | --- | --- |
| POST | `/auth/login` | username, password → accessToken, expiresIn, user |
| POST | `/orders/parse-comment` | `{ "comment": "0987654321 A1 2" }` → identities, items, customer, isBlacklisted, warnings |
| POST | `/orders/fast-create` | Multipart order JSON + proof tùy chọn |
| GET | `/orders` | page, size, phone, tiktokId, orderCode, status, channel |
| GET | `/orders/{id}` | Chi tiết đơn |
| PUT | `/orders/{id}/status` | `{ "status": "SHIPPING" }` |
| PUT | `/orders/{id}/payment` | depositAmount, paidAmount, paymentMethod |
| PUT | `/orders/{id}/tracking` | `{ "trackingCode": "GHN123" }` |
| GET/POST | `/categories` | Xem / tạo (admin) |
| GET/POST | `/products` | Xem phân trang / tạo (admin) |
| PUT | `/products/{id}` | Cập nhật sản phẩm/kho (admin); shortCode giữ nguyên |
| GET/POST | `/customers` | Xem phân trang / tạo |
| PUT | `/customers/{id}/blacklist` | isBlacklisted, notes (admin) |
| POST | `/users` | username, password, fullName, role (admin) |
| GET/POST | `/live-sessions` | Xem phân trang / tạo title, tiktokRoomId, notes |
| PUT | `/live-sessions/{id}/end` | Kết thúc phiên |
| POST | `/live-sessions/{id}/comments` | Connector gửi eventId, comment, tiktokId tùy chọn |
| GET | `/proofs/{uuid}.png` | Đọc ảnh có JWT; proofImageUrl là đường dẫn tương đối |

Các bộ lọc đơn kết hợp bằng AND, đối sánh chính xác (SĐT được chuẩn hóa +84 → 0, TikTok ID được bỏ @ và lowercase).
Không cho `userId` trong payload quyết định nhân viên tạo đơn.

## Fast-create

Multipart part `order` **phải có Content-Type application/json**, part `proof` chứa PNG/JPEG tối đa 5 MB.

```json
{
  "requestId": "9fb99c32-3d09-4d26-b6b3-dc8a7edc40c0",
  "customerName": "Khách livestream",
  "phoneNumber": "0987654321",
  "tiktokId": "nicktiktok",
  "address": "Địa chỉ giao hàng",
  "items": [{ "shortCode": "A1", "quantity": 2 }],
  "depositAmount": 50000,
  "shippingFee": 30000,
  "paymentMethod": "COD",
  "channel": "TIKTOK",
  "commentRaw": "0987654321 A1 2",
  "acknowledgeBlacklist": false,
  "status": "CONFIRMED"
}
```

`customerId` và `liveSessionId` tùy chọn. Nếu không có customerId, cần phoneNumber/tiktokId/facebookId.
Nếu các định danh thuộc hồ sơ khác nhau, backend trả 409 để nhân viên kiểm tra thay vì tự gộp.
Short code được uppercase. Dòng trùng mã được gộp; mỗi mã tối đa 10000 và mỗi request tối đa 50 dòng.
Giá do backend lấy từ sản phẩm, client không tự gửi giá để quyết định tổng tiền.

Ví dụ PowerShell sau khi lưu JSON thành `order.json` và có ảnh `comment.png`:

```powershell
curl.exe 'http://localhost:8080/api/v1/orders/fast-create' `
  -H "Authorization: Bearer $token" `
  -F 'order=<order.json;type=application/json' `
  -F 'proof=@comment.png;type=image/png'
```

Đơn đầu tiên trả HTTP 200 và đầy đủ OrderView; gửi lại cùng requestId trả cùng đơn.
Khi thao tác mới, tạo UUID mới. Lỗi 400: dữ liệu sai, 401: cần login, 403: thiếu quyền,
409: hết kho/identity conflict/trạng thái/blacklist cần xác nhận, 413: file lớn.

## Connector và WebSocket

Kết nối `ws://localhost:8080/api/v1/live-comments?liveSessionId=1` với Bearer header ở HTTP handshake.
Release dùng `wss://`. Endpoint WebSocket chỉ đọc; gửi comment vào REST ingest:

```json
{ "eventId": "provider-event-unique-123", "comment": "0987654321 A1 2", "tiktokId": "nicktiktok" }
```

Client nhận:

```json
{ "eventId": "provider-event-unique-123", "liveSessionId": 1, "comment": "0987654321 A1 2", "tiktokId": "nicktiktok" }
```

Chỉ subscriber đúng phiên nhận sự kiện. eventId trùng trong 5 phút không được phát lại.
Connector có thể dùng tài khoản staff riêng, login lấy token và gửi các sự kiện từ provider được cấu hình.
Không gắn token vào URL hoặc tự tạo đơn khi chưa có nhân viên kiểm tra.

### Settings management APIs

All routes below use `/api/v1` and Bearer JWT. Staff may read `GET /users/me` and search `GET /products?q=&page=&size=`; public product responses omit cost price.

Admin routes:
- `GET /management/products?q=&page=&size=` includes cost price; existing `POST /products`, `PUT /products/{id}` create/update. `DELETE /products/{id}` archives (INACTIVE), retaining order history.
- `POST /management/product-images`: multipart field `image`, maximum 5 MB, returns `imageUrl`. Uses the existing authenticated proof/image storage.
- `GET /management/blacklist?q=&page=&size=`; `POST /management/blacklist` accepts `{id?, name?, phoneNumber?, tiktokId?, notes}`. Requires phone or TikTok ID and reason. Without id, matches existing contact; with id, edits the customer. `PUT /customers/{id}/blacklist` with `{isBlacklisted:false, notes?}` removes the flag.
- `GET /users?q=&page=&size=`; existing `POST /users` creates an employee/admin. `PUT /users/{id}` accepts `{fullName,role,isActive,password?}`, where roles are `ROLE_STAFF` or `ROLE_ADMIN`; omitted/empty password leaves it unchanged. Admin cannot disable or demote the current account. Password reset does not revoke previously issued tokens; disabling an account rejects subsequent API requests.

Lists use page 0 and size 20 by default, maximum size 100. Products retain their original short code after creation.

### Mã hàng phát sinh khi livestream

Trong form Chốt đơn nhanh, có thể nhập mã chưa được tạo trong danh mục. Nhập giá bán cho từng mã mới rồi bấm Chốt đơn ngay: backend tạo sản phẩm và đơn trong cùng giao dịch. Tên sản phẩm bằng mã viết hoa, giá vốn ban đầu 0 (chưa được bổ sung), trạng thái ACTIVE; số lượng nhập ban đầu bằng lượng của đơn và được trừ ngay nên tồn sau chốt là 0. Admin bổ sung tên/giá vốn/tồn kho trong Cài đặt → Sản phẩm. Mã đã có luôn dùng giá và kiểm tra tồn kho trên server, không cho giá nhập nhanh ghi đè. Chốt thất bại không để lại sản phẩm mới; retry cùng requestId không tạo đơn/sản phẩm trùng.

API `orders/fast-create`: mỗi dòng `items` hỗ trợ `newProductPrice` (số không âm, tối đa 2 chữ số thập phân), chỉ dùng để tạo mã chưa có. Ví dụ `{"shortCode":"LIVE99","quantity":2,"newProductPrice":125000}`. Thiếu giá cho mã mới trả lỗi nhập giá, không yêu cầu tạo sản phẩm trước trong danh mục.

### Đơn nháp chưa có mã hàng và bàn phím popup

Để trống mã hàng trong popup Chốt đơn để lưu DRAFT; cần có thông tin khách hoặc comment. API `/orders/fast-create` chấp nhận `items: []` và luôn ép trạng thái DRAFT cho trường hợp này, không giữ kho. Comment/ảnh bằng chứng được giữ lại, retry requestId không tạo trùng. Đơn chưa có hàng không được xác nhận hoặc giao đi. Chưa có tiền hàng thì không nhập cọc vượt tổng tiền.

Trong tab Đơn hàng, nhấn đơn nháp → **Bổ sung mã hàng cho đơn nháp** → nhập mã/số lượng, giá nếu mã mới → Lưu → **Đổi trạng thái → CONFIRMED**. API `PUT /api/v1/orders/{id}/items` nhận `{items:[{shortCode,quantity,newProductPrice?}]}`; chỉ bổ sung cho đơn nháp chưa có hàng. Retry cùng danh sách không giữ kho hai lần, thay danh sách đã lưu bị từ chối. Khi bổ sung, kiểm tra giá/tồn kho và cập nhật doanh thu phiên live cùng giao dịch. Có thể bổ sung sau khi phiên live kết thúc.

Popup dùng Android Views: ẩn IME và clear focus khi xác nhận, vuốt hoặc chạm ra ngoài ô nhập; nút **Ẩn bàn phím** là thao tác trực tiếp. BottomSheet mở expanded, dùng adjustResize và padding theo IME/system insets để cuộn được tới nút xác nhận khi bàn phím mở.

Migration V3 cho phép khách tạm chưa có thông tin liên hệ và đơn DRAFT chưa giữ kho. Chỉ cập nhật hai ràng buộc liên quan; không xóa bảng/dữ liệu. Các API tạo khách thông thường vẫn yêu cầu thông tin liên hệ.
