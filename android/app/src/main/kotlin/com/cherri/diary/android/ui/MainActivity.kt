package com.cherri.diary.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.cherri.diary.android.cherri
import com.cherri.diary.android.data.*
import com.cherri.diary.android.live.FloatingWindowService
import com.cherri.diary.android.live.LiveCommentClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var phone: EditText
    private lateinit var nick: EditText
    private lateinit var code: EditText
    private lateinit var status: Spinner
    private lateinit var channel: Spinner
    private var page = 0
    private var liveClient: LiveCommentClient? = null
    private lateinit var liveResults: LinearLayout
    private var manualComment = ""
    private val photo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) network {
            val file = withContext(Dispatchers.IO) { copyProof(uri) }
            QuickOrderBottomSheet(this, lifecycleScope, file, manualComment).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (cherri.tokens.token() == null) { login(); return }
        body = column()
        setContentView(ScrollView(this).apply { addView(body) })
        body.label("Cherri Diary · ${cherri.tokens.name()}").textSize = 24f
        body.label("${cherri.tokens.baseUrl()}\nQuyền đọc comment và chụp màn hình do bạn bật; luôn kiểm tra dữ liệu trước khi chốt.")
        body.button("Cấp quyền nút nổi") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())) }
        body.button("Cấu hình đọc comment TikTok") {
            AlertDialog.Builder(this).setTitle("Đọc comment đã chọn")
                .setMessage("Cherri Diary chỉ đọc nội dung bạn bấm/chọn trên TikTok để điền đơn. Bạn có thể tắt trong Cài đặt. Nếu TikTok không cung cấp text, hãy nhập comment thủ công.")
                .setPositiveButton("Mở Cài đặt") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Đóng", null).show()
        }
        body.button("Bật nút Chốt Đơn Live") {
            if (!Settings.canDrawOverlays(this)) toast("Cấp quyền nút nổi trước")
            else {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                ContextCompat.startForegroundService(this, Intent(this, FloatingWindowService::class.java))
                toast("Mở TikTok, chọn comment rồi bấm nút nổi. Android sẽ hỏi quyền chụp màn hình.")
            }
        }
        body.button("Tắt nút nổi") { stopService(Intent(this, FloatingWindowService::class.java)) }
        val comment = body.field("Nhập comment để chốt thủ công")
        body.button("Chốt đơn từ comment") { QuickOrderBottomSheet(this, lifecycleScope, commentRaw = comment.text.toString()).show() }
        body.button("Chốt đơn kèm ảnh bill / comment đã lưu") { manualComment = comment.text.toString(); photo.launch("image/*") }
        val liveTitle = body.field("Tên phiên live")
        val room = body.field("TikTok room ID (nếu có)")
        val session = body.field("ID phiên live", cherri.tokens.liveSessionId()?.toString() ?: "")
        body.button("Tạo phiên live") { network {
            val result = cherri.api.service().createSession(LiveSessionRequest(liveTitle.text.toString(), room.text.toString().ifBlank { null }))
            cherri.tokens.setLiveSessionId(result.id); session.setText(result.id.toString()); toast("Đã tạo phiên ${result.id}")
        } }
        body.button("Dùng ID phiên này / kết nối comment gateway") {
            val id = session.text.toString().toLongOrNull()?.takeIf { it > 0 }
            cherri.tokens.setLiveSessionId(id)
            if (id == null) toast("Đã bỏ gắn phiên live") else connectLive(id)
        }
        body.button("Kết thúc phiên live") { network {
            val id = cherri.tokens.liveSessionId() ?: error("Chưa chọn phiên live")
            cherri.api.service().endSession(id); cherri.tokens.setLiveSessionId(null); session.text.clear(); liveClient?.close(); toast("Phiên live đã kết thúc")
        } }
        liveResults = column().also(body::addView)
        body.label("Quản lý đơn hàng")
        phone = body.field("Lọc SĐT")
        nick = body.field("Lọc TikTok ID")
        code = body.field("Lọc mã đơn")
        status = body.choice(listOf("TẤT CẢ", "DRAFT", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED"))
        channel = body.choice(listOf("TẤT CẢ", "TIKTOK", "FACEBOOK", "ZALO", "PHONE"))
        body.button("Tìm đơn / làm mới") { page = 0; loadOrders() }
        body.button("Trang trước") { if (page > 0) { page--; loadOrders() } }
        body.button("Trang sau") { page++; loadOrders() }
        body.button("Xem hàng và tồn kho") { network {
            val products = cherri.api.service().products()
            results.removeAllViews()
            results.label("${products.totalElements} sản phẩm (tối đa 100 mã trên màn hình này)")
            products.content.forEach { results.label("${it.shortCode} · ${it.name}\n${money(it.sellingPrice)} · Kho ${it.stockQuantity} · ${it.status}") }
        } }
        body.button("Đăng xuất") {
            stopService(Intent(this, FloatingWindowService::class.java)); liveClient?.close(); cherri.tokens.clear(); login()
        }
        results = column().also(body::addView)
        loadOrders()
    }

    private fun connectLive(id: Long) {
        liveClient?.close()
        liveClient = LiveCommentClient(cherri.api, cherri.tokens, id,
            onComment = { event -> runOnUiThread {
                if (!isFinishing) {
                    if (liveResults.childCount >= 20) liveResults.removeViewAt(0)
                    liveResults.button("${event.tiktokId ?: "Khách"}: ${event.comment}") {
                        val raw = (event.tiktokId?.let { "@$it " } ?: "") + event.comment
                        QuickOrderBottomSheet(this, lifecycleScope, commentRaw = raw).show()
                    }
                }
            } }, onError = { message -> runOnUiThread { toast(message) } })
        liveClient?.connect()
        toast("Đã yêu cầu kết nối gateway. Cần connector bên ngoài gửi comment vào backend.")
    }
    private fun loadOrders() = network {
        val data = cherri.api.service().orders(phone.text.toString().ifBlank { null }, nick.text.toString().ifBlank { null },
            code.text.toString().ifBlank { null }, status.selectedItem.toString().takeUnless { status.selectedItemPosition == 0 },
            channel.selectedItem.toString().takeUnless { channel.selectedItemPosition == 0 }, page)
        results.removeAllViews()
        results.label("Trang ${data.page + 1}/${data.totalPages.coerceAtLeast(1)} · ${data.totalElements} đơn")
        data.content.forEach { order ->
            results.label("${order.orderCode}\n${order.customer.name} · ${order.channel} · ${order.status}\n${order.items.joinToString { "${it.shortCode} × ${it.quantity}" }}\nCòn thu: ${money(order.remainingAmount)} · ${order.paymentStatus}")
            results.button("Trạng thái / thanh toán / vận đơn") { manageOrder(order) }
        }
    }
    private fun manageOrder(order: OrderView) {
        AlertDialog.Builder(this).setTitle(order.orderCode).setItems(arrayOf("Đổi trạng thái", "Ghi nhận thanh toán", "Mã vận đơn")) { _, option ->
            when (option) {
                0 -> {
                    val targets = when (order.status) {
                        "DRAFT" -> arrayOf("CONFIRMED", "CANCELLED")
                        "CONFIRMED" -> arrayOf("SHIPPING", "CANCELLED")
                        "SHIPPING" -> arrayOf("COMPLETED", "CANCELLED")
                        else -> emptyArray()
                    }
                    if (targets.isEmpty()) toast("Đơn đã kết thúc")
                    else AlertDialog.Builder(this).setTitle("Chọn trạng thái").setItems(targets) { _, selected ->
                        AlertDialog.Builder(this).setMessage("Chuyển đơn sang ${targets[selected]}?")
                            .setPositiveButton("Xác nhận") { _, _ -> network { cherri.api.service().status(order.id, StatusRequest(targets[selected])); loadOrders() } }
                            .setNegativeButton("Đóng", null).show()
                    }.show()
                }
                1 -> {
                    val form = column()
                    form.label("Nhập số tiền đã thu ngoài cọc (giá trị tổng, không phải khoản cộng thêm)")
                    val deposit = form.field("Cọc", order.depositAmount.toPlainString())
                    val paid = form.field("Đã thu ngoài cọc", order.paidAmount.toPlainString())
                    val method = form.choice(listOf("COD", "BANK_TRANSFER")); method.setSelection(if (order.paymentMethod == "COD") 0 else 1)
                    AlertDialog.Builder(this).setTitle("Thanh toán").setView(form).setPositiveButton("Lưu") { _, _ -> network {
                        cherri.api.service().payment(order.id, PaymentRequest(BigDecimal(deposit.text.toString()), BigDecimal(paid.text.toString()), method.selectedItem.toString())); loadOrders()
                    } }.setNegativeButton("Đóng", null).show()
                }
                2 -> {
                    val input = EditText(this).apply { setText(order.trackingCode ?: ""); hint = "Mã vận đơn" }
                    AlertDialog.Builder(this).setTitle("Vận đơn").setView(input).setPositiveButton("Lưu") { _, _ -> network {
                        cherri.api.service().tracking(order.id, TrackingRequest(input.text.toString())); loadOrders()
                    } }.setNegativeButton("Đóng", null).show()
                }
            }
        }.show()
    }
    private fun network(block: suspend () -> Unit) { lifecycleScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { toast(errorText(e)); if (cherri.tokens.token() == null) login() }
    } }
    private fun copyProof(uri: Uri): File {
        val directory = File(cacheDir, "proofs").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Không đọc được ảnh" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        total += read; require(total <= 5 * 1024 * 1024) { "Ảnh tối đa 5 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 20_000_000) { "Ảnh không hợp lệ hoặc vượt 20 megapixel" }
            require(options.outMimeType in setOf("image/png", "image/jpeg")) { "Chỉ nhận ảnh PNG hoặc JPEG" }
            return file
        } catch (e: Exception) { file.delete(); throw e }
    }
    private fun login() { startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish() }
    override fun onDestroy() { liveClient?.close(); super.onDestroy() }
}
