# Cherri Compose UI

Giao diện đăng nhập, báo cáo tổng quan, Live Comment và thanh điều hướng của app đã dùng các component trong `android/app/src/main/kotlin/com/cherri/diary/android/ui/design`.

- `CherriTheme.kt`: Raspberry #D81B60, Cherry #E53935, nền kem, chữ #212121/#616161 và góc 16–24dp.
- `CherriLoginScreen.kt`: nền gradient, logo hiện có, card 24dp/elevation 4dp, ô nhập có icon; mật khẩu không lưu vào saved state.
- `CherriComponents.kt`: `CommentCard`, avatar chữ cái, badge pastel, `BottomNavigationBar` Material 3 và `CommentSkeleton`.
- `CherriDashboard.kt`: báo cáo từ dữ liệu API thật; giá trị trên tối đa 100 đơn gần đây, không giả định là doanh thu toàn bộ ngày.
- `CherriSampleApp.kt`: mẫu Compose độc lập, bốn tab, dữ liệu minh họa, trạng thái chốt mẫu và shimmer khi vào tab Live.

Để chạy riêng mẫu trong một `ComponentActivity`/`AppCompatActivity`, gọi trong `onCreate` sau `super.onCreate`:

```kotlin
import androidx.activity.compose.setContent
import com.cherri.diary.android.ui.design.CherriSampleApp

setContent { CherriSampleApp() }
```

Mẫu không gọi API. App thật dùng `LoginActivity` và `MainActivity`, giữ xác thực, API và luồng tạo đơn hiện có. Tab đơn hàng và cài đặt còn tích hợp View qua `AndroidView`. “Đã chốt” chỉ được hiển thị với trạng thái xác nhận; feed thật chưa có ánh xạ eventId → trạng thái đơn nên không tự gán nhãn này khi mới mở form. “Thiếu SĐT” chỉ là gợi ý từ nội dung comment, bước chốt vẫn kiểm tra khách hàng qua backend.

Từ PowerShell ở thư mục gốc:

```powershell
& 'D:\Tool\Git\bin\bash.exe' ./scripts/Deploy-Android.sh
```

APK được chép vào thư mục `Compose Deploy App` nằm cạnh project. Cài APK để xem giao diện trên thiết bị.
