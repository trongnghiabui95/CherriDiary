# Cherri Diary

Hệ thống hỗ trợ chốt đơn livestream và quản lý đơn TikTok, Facebook, Zalo, Phone:
backend Kotlin Spring Boot 3.5.6 + PostgreSQL, app Android Kotlin Native + XML Views,
Retrofit 2, Coroutines, JWT và nút nổi. Tên project là `CherriDiary`; đường dẫn hiện tại vẫn là `CherriDairy`.

## 1. SQL DDL

Script đầy đủ nằm tại [V1__initial_schema.sql](backend/src/main/resources/db/migration/V1__initial_schema.sql).
Flyway tự chạy script trên database trống; Hibernate chỉ kiểm tra schema bằng `ddl-auto=validate`.
Có bảy bảng theo yêu cầu, FK, CHECK, UNIQUE và các index phục vụ tra cứu/phân trang.
SĐT có thể null khi khách mới chỉ có TikTok ID. TikTok/Facebook ID có UNIQUE để không tạo hai hồ sơ cho cùng định danh.
Các UNIQUE constraint đã tạo index, không cần thêm một index trùng trên cùng cột.

## 2. Cấu trúc dự án

```text
CherriDairy/
├── backend/                         # Mở bằng IntelliJ IDEA
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/cherri/diary/
│       │   ├── api/                 # DTO, validation, REST controllers, lỗi API
│       │   ├── domain/              # JPA entities, enums, repositories
│       │   ├── security/            # JWT filter, BCrypt, bootstrap admin
│       │   ├── service/             # Parser, kho, thanh toán, lưu ảnh
│       │   └── live/                # REST ingest + WebSocket comment gateway
│       ├── main/resources/db/migration/
│       └── test/                    # Integration tests với PostgreSQL thật
├── android/                         # Mở riêng bằng Android Studio
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/cherri/diary/android/
│       │   ├── data/                # TokenManager, Retrofit, AuthInterceptor
│       │   ├── live/                # Overlay, Accessibility, MediaProjection
│       │   └── ui/                  # Login, cấu hình, BottomSheet, quản lý đơn
│       └── res/                     # Theme, icon, config Accessibility/backup
├── docs/                            # Kiến trúc, API, checklist thiết bị
├── scripts/                         # Khởi tạo cấu hình và chạy backend Windows
├── compose.yml                      # PostgreSQL 16 cho môi trường local
└── gradlew / gradlew.bat             # Wrapper chung cho cả hai project
```

Backend và Android có settings Gradle riêng để build backend không cần Android SDK.

## 3. Kotlin Backend

- [CommentParserService.kt](backend/src/main/kotlin/com/cherri/diary/service/CommentParserService.kt): nhận diện SĐT, `@nick`, nhiều mã hàng, số lượng, blacklist và cảnh báo comment mơ hồ.
- [Entities.kt](backend/src/main/kotlin/com/cherri/diary/domain/Entities.kt) và [Repositories.kt](backend/src/main/kotlin/com/cherri/diary/domain/Repositories.kt): JPA entities thường để tránh `equals/toString` đệ quy; DTO là Kotlin data classes.
- [OrderService.kt](backend/src/main/kotlin/com/cherri/diary/service/OrderService.kt): transaction, pessimistic locking, refresh sau khóa, snapshot giá, trừ/hoàn kho, idempotency.
- [Controllers.kt](backend/src/main/kotlin/com/cherri/diary/api/Controllers.kt): API theo yêu cầu và API danh mục/sản phẩm/khách/nhân viên/phiên live.
- [JwtAuthenticationFilter.kt](backend/src/main/kotlin/com/cherri/diary/security/JwtAuthenticationFilter.kt): HS256, hạn token, issuer, tài khoản còn hoạt động, phân quyền admin/staff.
- [LiveCommentGateway.kt](backend/src/main/kotlin/com/cherri/diary/live/LiveCommentGateway.kt): gateway đã có REST ingest và WebSocket được bảo vệ bằng JWT.

Chi tiết hợp đồng và ví dụ: [docs/API.md](docs/API.md), [docs/requests.http](docs/requests.http).
Tiền và trạng thái: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Chạy backend trên Windows

Cần JDK 26 và PostgreSQL 16, hoặc Docker Desktop để chạy database bằng Compose. Backend dùng Gradle 9.6, Kotlin 2.4.20 và Spring Boot 4.1.1.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Initialize-DevConfig.ps1
# Tạo .env với cấu hình DB và JWT/admin secret ngẫu nhiên, không ghi đè file đã có.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Backend.ps1 -StartDatabase
```

Nếu đã có PostgreSQL, tạo database `cherridiary_db`, cấu hình `DB_URL`, `DB_USER`, `DB_PASSWORD`
trong `.env` và chạy `Start-Backend.ps1` không thêm `-StartDatabase`.
Backend chạy tại `http://localhost:8080`. Tài khoản admin ban đầu lấy từ `ADMIN_USERNAME`/`ADMIN_PASSWORD` trong `.env`.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`; OpenAPI JSON: `http://localhost:8080/v3/api-docs`.
Trong Swagger, mở `POST /api/v1/auth/login` → **Try it out** → nhập tài khoản → **Execute**.
Copy `accessToken`, bấm **Authorize**, dán token (không thêm `Bearer `) rồi thử các API khác.
Có thể mở Swagger qua URL ngrok + `/swagger-ui/index.html`. Trang tài liệu mở công khai; API nghiệp vụ vẫn yêu cầu JWT và phân quyền.
Bootstrap chỉ tạo tài khoản chưa tồn tại; đổi biến môi trường không tự đổi mật khẩu tài khoản cũ.
Script `Start-Backend.ps1` mặc định bật profile `local`, cho phép mật khẩu admin ngắn để chạy trên máy cá nhân.
Các profile khác vẫn yêu cầu mật khẩu bootstrap ít nhất 12 ký tự. Tài khoản đã có không bị tạo lại hoặc đổi mật khẩu khi restart.
Hibernate dùng `ddl-auto=validate` để kiểm tra schema; Flyway `clean-disabled=true` và không xóa database/bảng khi khởi động lại.

Nếu đã chạy V1 SQL thủ công và gặp lỗi `Found non-empty schema(s) "public" but no schema history table`,
đối chiếu schema hiện tại với V1 rồi ghi nhận baseline phiên bản 1 trong một lần khởi động:

```powershell
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
$env:SPRING_FLYWAY_BASELINE_VERSION = '1'
.\scripts\Start-Backend.ps1
```

Baseline ghi lịch sử cho schema đã có, giữ nguyên bảng và dữ liệu, bỏ qua việc chạy lại V1.
Sau khi dừng backend bằng Ctrl+C, bỏ thiết lập cho phiên terminal này rồi khởi động như bình thường:

```powershell
Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_FLYWAY_BASELINE_VERSION -ErrorAction SilentlyContinue
```

Kết nối DB local mặc định: `localhost:5432/cherridiary_db`, user `admin`, password `123456`.
Mật khẩu đăng nhập ứng dụng lấy từ `ADMIN_PASSWORD`, độc lập với mật khẩu PostgreSQL.

Trên Linux/macOS, export các biến trong `.env`, chạy `docker compose up -d postgres-db`, rồi `bash gradlew :backend:bootRun`.

## 4. Kotlin Android

### Nhận comment TikTok tự động

Connector Docker đã có trong [tiktok-connector/README.md](tiktok-connector/README.md).
Chạy `bash scripts/Deploy-TikTok-Connector.sh --prepare`, điền `.env.connector` ở folder deploy,
rồi chạy `bash scripts/Deploy-TikTok-Connector.sh`. Worker nhận CHAT từ username TikTok đang live và
đẩy tới ID phiên Cherri Diary; Android kết nối gateway của ID đó để hiển thị comment mới.

Màn hình chính có 4 tab dưới: **Tổng quan**, **Live**, **Đơn hàng**, **Cài đặt**.
Chuyển tab giữ form live và vị trí cuộn trong cùng màn hình. Tổng quan hiển thị tổng số đơn,
giá trị/đã thu/còn thu và biểu đồ trạng thái trên tối đa 100 đơn gần nhất; không phải báo cáo doanh thu toàn kỳ.
Tab Đơn hàng có danh mục sản phẩm và thao tác **In đơn / Lưu PDF** qua hệ thống in Android.
Tab Cài đặt cho phép đổi server (cần đăng nhập lại), điều khiển quyền/nút nổi và chọn giấy A4/A5.
Login dùng icon tùy chỉnh, nền pastel, hiện/ẩn mật khẩu và **Ghi nhớ đăng nhập**:
nếu bỏ chọn, token chỉ giữ trong tiến trình app; nếu chọn, token được lưu mã hóa đến khi hết hạn hoặc đăng xuất.
**Quên mật khẩu** hướng dẫn liên hệ quản trị viên; backend chưa có API đặt lại mật khẩu qua email.

### Deploy bằng Git Bash

Chạy từ thư mục gốc project:

```bash
bash scripts/Deploy-Backend.sh
bash scripts/Deploy-Android.sh
```

Đích mặc định: thư mục `Compose Deploy App` nằm cạnh thư mục project (trên máy này là `D:\Cherri Project\Compose Deploy App`). Backend build `bootJar`, chép thành `backend.jar`,
dừng và tạo lại riêng service `backend` bằng Compose chạy nền. Folder đích cần Compose có service `backend`
và `.env` đã cấu hình. Service `backend` phải dùng image có Java 26 vì JAR được biên dịch cho JVM 26.
Dừng backend chạy trong terminal cũ trước nếu nó đang dùng cổng 8080.
Android dùng wrapper riêng với Gradle 9.5 và toolchain JDK 21 (bytecode JVM 17), build bản debug và chép `app-debug.apk`; cài APK đó lên thiết bị để cập nhật app.
Có thể truyền đường dẫn đích khác, ví dụ `bash scripts/Deploy-Android.sh "/d/My Deploy"`.

- [TokenManager.kt](android/app/src/main/kotlin/com/cherri/diary/android/data/TokenManager.kt): EncryptedSharedPreferences + Android Keystore, tắt backup dữ liệu phiên.
- [AuthInterceptor.kt](android/app/src/main/kotlin/com/cherri/diary/android/data/AuthInterceptor.kt), [ApiService.kt](android/app/src/main/kotlin/com/cherri/diary/android/data/ApiService.kt): Bearer JWT, Retrofit suspend API, multipart JSON `order` + file `proof`.
- [FloatingWindowService.kt](android/app/src/main/kotlin/com/cherri/diary/android/live/FloatingWindowService.kt): nút tròn kéo được, foreground notification và form BottomSheet kiểu overlay.
- [TikTokAccessibilityService.kt](android/app/src/main/kotlin/com/cherri/diary/android/live/TikTokAccessibilityService.kt): lưu tạm node comment vừa bấm/chọn, lấy text và bounds nếu TikTok cung cấp.
- [ScreenshotCaptureService.kt](android/app/src/main/kotlin/com/cherri/diary/android/live/ScreenshotCaptureService.kt): MediaProjection, một VirtualDisplay cho mỗi phiên được cấp quyền, nhiều lần chụp trong cùng phiên, cắt theo bounds và xử lý xoay màn hình.
- [QuickOrderBottomSheet.kt](android/app/src/main/kotlin/com/cherri/diary/android/ui/QuickOrderBottomSheet.kt): phân tích/sửa comment, preview ảnh, mã hàng, cọc, ship, COD, cảnh báo blacklist, gửi multipart và gửi lại cùng requestId.
- [LoginActivity.kt](android/app/src/main/kotlin/com/cherri/diary/android/ui/LoginActivity.kt), [MainActivity.kt](android/app/src/main/kotlin/com/cherri/diary/android/ui/MainActivity.kt): đăng nhập, cấu hình quyền, chọn ảnh bill, phiên live, danh sách đơn và cập nhật trạng thái/thanh toán/vận đơn.

### Build và sử dụng

Cần Android SDK Platform 35 + Build Tools 35.0.0. Mở thư mục `android/` bằng Android Studio hoặc khai báo `sdk.dir` trong `android/local.properties`.

```powershell
.\android\gradlew.bat -p android :app:assembleDebug :app:lintDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Emulator dùng URL `http://10.0.2.2:8080/api/v1/`; điện thoại thật dùng IP LAN của máy chạy backend.
Chỉ bản debug cho HTTP; bản release yêu cầu HTTPS. Nhập URL tại màn hình đăng nhập.

1. Đăng nhập, cấp quyền hiển thị nút nổi và bật dịch vụ đọc comment nếu muốn.
2. Bật nút nổi, mở TikTok, bấm/chọn comment rồi bấm **Chốt Đơn Live**.
3. Cấp quyền chia sẻ màn hình cho phiên chụp đầu tiên. Những lần chụp sau dùng cùng phiên cho đến khi bạn tắt hoặc Android thu hồi quyền.
4. Kiểm tra ảnh, comment, khách, mã hàng, cọc/ship/COD và xác nhận.
5. Tắt nút nổi hoặc dùng nút tắt trong notification để kết thúc phiên chụp.

Nếu TikTok không cung cấp node text, nhập comment thủ công. Nếu không có bounds, ảnh là toàn màn hình để nhân viên kiểm tra.
Ảnh màn hình không thay cho OCR; project không suy đoán comment từ các pixel.
PixelCopy không chụp được tùy ý nội dung một ứng dụng khác, nên luồng này dùng MediaProjection.

## Kiểm tra

```powershell
.\gradlew.bat :backend:test :backend:bootJar
.\android\gradlew.bat -p android :app:assembleDebug :app:lintDebug
```

Backend tests tự khởi tạo PostgreSQL 17.6 bằng binary embedded trên cổng ngẫu nhiên, chạy Flyway, validate mapping rồi kiểm tra transaction và HTTP/WebSocket; không cần Docker cho tests.
Linux cần chạy tests bằng tài khoản không phải root. Android APK build và lint không thay thế kiểm tra nút nổi/chụp màn hình trên thiết bị thật.
Checklist: [docs/DEVICE_TESTING.md](docs/DEVICE_TESTING.md).

## Phạm vi kết nối TikTok

Gateway nhận comment chuẩn hóa từ connector bên ngoài qua `POST /api/v1/live-sessions/{id}/comments`
và chuyển đến app qua WebSocket. Worker TikTok trong `tiktok-connector/` đã được triển khai; cần cấu hình username đang LIVE,
ID phiên Cherri và tài khoản Cherri trong `.env.connector` để kết nối thật.
Đường chốt trực tiếp trên điện thoại dùng Accessibility + MediaProjection và luôn có bước nhân viên kiểm tra.
Facebook/Zalo/Phone được lưu thành kênh đơn hàng; chưa có webhook nhập đơn tự động từ các nền tảng đó.

### Quản lý từ Cài đặt (Android)

Mở tab **Cài đặt** → chọn **Sản phẩm**, **Danh sách đen** hoặc **Tài khoản**. Các màn hình quản lý sử dụng Jetpack Compose/NavHost, tìm kiếm trên server và phân trang.

- **Sản phẩm:** Admin chọn **Thêm sản phẩm** hoặc **Sửa** trên từng thẻ; nhập mã ngắn, tên, giá bán, giá vốn và tồn kho. Có thể chọn ảnh từ máy (tối đa 5 MB) hoặc nhập URL ảnh. Mã ngắn giữ cố định sau khi tạo. **Gỡ / Ngừng dùng** chuyển sản phẩm sang ngừng bán; lịch sử đơn không bị xóa. Nhân viên chỉ xem danh sách và giá bán.
- **Danh sách đen:** Admin nhập SĐT hoặc TikTok ID cùng lý do; có thể sửa hoặc gỡ chặn. Thêm bằng thông tin khách đã có sẽ cập nhật khách đó, giữ thông tin liên hệ còn lại. Gỡ chặn không xóa khách hoặc lịch sử đơn.
- **Tài khoản:** xem tài khoản đang đăng nhập và đăng xuất. Admin có nút **Quản lý nhân viên** để thêm tài khoản, đổi tên, phân quyền, đặt mật khẩu mới hoặc khóa/mở khóa. Mật khẩu mới tối thiểu 12 ký tự, tối đa 72 byte UTF-8. Không thể tự khóa/hạ quyền Admin đang sử dụng. Khóa tài khoản có hiệu lực với các yêu cầu API tiếp theo; đặt lại mật khẩu chưa thu hồi các token đã cấp, chúng hết hạn theo cấu hình JWT.

Các API quản trị kiểm tra quyền Admin trên backend; giá vốn chỉ trả trong `/api/v1/management/products`. Ảnh tải lên lưu trong thư mục uploads đang được mount của backend, không lưu trong APK.

### Danh sách comment Live nhỏ gọn

Tab Live dùng Compose LazyColumn trong vùng chiều cao còn lại (không cuộn lồng nhau). Mỗi hàng có nickname, username, giờ nhận tại ứng dụng (HH:mm:ss theo múi giờ điện thoại), nội dung tối đa hai dòng và nút Chốt đơn nhỏ bên phải; nội dung đầy đủ được truyền vào popup chốt đơn. Giữ tối đa 200 comment trong bộ nhớ; số đếm là tổng số đã nhận trong lượt kết nối.

Khi ở đầu danh sách, theo dõi comment mới. Khi cuộn xem comment cũ, stable keys giữ vị trí đang xem; nút **↑ Comment mới nhất** dùng animateScrollToItem(0). Comment quá giới hạn 200 bị loại khỏi cuối danh sách. Cấu hình TikTok LIVE tự thu gọn sau khi nhận comment đầu tiên; bấm Cấu hình để sửa username, chọn ID phiên có sẵn hoặc kết thúc phiên. Chốt thủ công, ảnh bill và nút nổi nằm trong thanh thao tác phía dưới.
