package com.cherri.diary.android.ui

import android.Manifest
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.cherri.diary.android.R
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.text.InputType
import com.google.android.material.switchmaterial.SwitchMaterial
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
import androidx.core.widget.NestedScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val tabPages = mutableListOf<View>()
    private lateinit var dashboard: LinearLayout
    private var selectedTab = 1
    private lateinit var results: LinearLayout
    private lateinit var phone: EditText
    private lateinit var nick: EditText
    private lateinit var code: EditText
    private lateinit var status: Spinner
    private lateinit var channel: Spinner
    private var page = 0
    private var totalPages = 1
    private var liveClient: LiveCommentClient? = null
    private lateinit var liveResults: LinearLayout
    private var manualComment = ""
    private lateinit var connectionBadge: TextView
    private lateinit var overlaySwitch: SwitchMaterial
    private lateinit var checkoutSwitch: SwitchMaterial
    private var syncingOverlay = false
    private var enableAfterPermission = false
    private var receiverRegistered = false
    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { syncOverlay() }
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (enableAfterPermission && Settings.canDrawOverlays(this)) startOverlay()
        else if (enableAfterPermission) toast("Chưa cấp quyền hiển thị nút nổi")
        enableAfterPermission = false
        syncOverlay()
    }
    private val photo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) network {
            val file = withContext(Dispatchers.IO) { copyProof(uri) }
            showCheckout(manualComment, file)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CherriDiary_Login)
        super.onCreate(savedInstanceState)
        if (cherri.tokens.token() == null) { login(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F2F4.toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        val header = column().apply { setPadding(dp(20), dp(12), dp(20), dp(4)) }
        header.label("Cherri Diary").apply { textSize = 24f; setTextColor(0xFF81213E.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        root.addView(header)
        val container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        repeat(4) {
            body = column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)) }
            val scroll = NestedScrollView(this).apply { addView(body); visibility = View.GONE }
            container.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            tabPages.add(scroll)
            when (it) {
                0 -> { dashboard = body; createDashboard() }
                1 -> { createLiveSection(); createCheckoutSection() }
                2 -> createOrdersSection()
                3 -> createSystemSection()
            }
        }
        (connectionBadge.parent as LinearLayout).removeView(connectionBadge)
        header.addView(connectionBadge)
        val navigation = BottomNavigationView(this).apply {
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            setBackgroundColor(android.graphics.Color.WHITE)
            menu.add(0, 1, 0, "Tổng quan").setIcon(R.drawable.ic_tab_dashboard)
            menu.add(0, 2, 1, "Live").setIcon(R.drawable.ic_tab_live)
            menu.add(0, 3, 2, "Đơn hàng").setIcon(R.drawable.ic_tab_orders)
            menu.add(0, 4, 3, "Cài đặt").setIcon(R.drawable.ic_tab_settings)
            setOnItemSelectedListener { item ->
                selectedTab = item.itemId
                tabPages.forEachIndexed { index, view -> view.visibility = if (index + 1 == selectedTab) View.VISIBLE else View.GONE }
                if (selectedTab == 1) loadDashboard()
                if (selectedTab == 3) loadOrders()
                true
            }
        }
        root.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        navigation.selectedItemId = savedInstanceState?.getInt("tab", 1) ?: 1
        tabPages[selectedTab - 1].visibility = View.VISIBLE
        loadDashboard()
        ContextCompat.registerReceiver(this, overlayReceiver, IntentFilter(FloatingWindowService.STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun createDashboard() {
        dashboard.featureCard("Xin chào, ${cherri.tokens.name()}", "Tổng quan hoạt động bán hàng").label("Đang tải…")
    }

    private fun loadDashboard(): Unit = network {
        val data = cherri.api.service().orders(size = 100)
        connection(true)
        dashboard.removeAllViews()
        val welcome = dashboard.featureCard("Xin chào, ${cherri.tokens.name()}", "Tổng quan hoạt động bán hàng")
        welcome.label("${data.totalElements} đơn trong hệ thống").apply { textSize = 26f; setTextColor(0xFFB22D54.toInt()) }
        welcome.action("Làm mới", "secondary") { loadDashboard() }
        val summary = dashboard.featureCard("Đơn gần đây", "Thống kê trên ${data.content.size} đơn mới nhất, tối đa 100 đơn")
        val active = data.content.filter { it.status != "CANCELLED" }
        summary.label("Giá trị đơn: ${money(active.fold(BigDecimal.ZERO) { total, order -> total + order.totalAmount })}")
        summary.label("Đã thu: ${money(active.fold(BigDecimal.ZERO) { total, order -> total + order.depositAmount + order.paidAmount })}")
        summary.label("Còn thu: ${money(active.fold(BigDecimal.ZERO) { total, order -> total + order.remainingAmount })}")
        val chart = dashboard.featureCard("Trạng thái đơn", "Phân bố các đơn gần đây")
        val labels = linkedMapOf("DRAFT" to "Nháp", "CONFIRMED" to "Đã chốt", "SHIPPING" to "Đang giao", "COMPLETED" to "Hoàn tất", "CANCELLED" to "Đã hủy")
        labels.forEach { (status, title) ->
            val count = data.content.count { it.status == status }
            chart.label("$title · $count")
            chart.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = data.content.size.coerceAtLeast(1); progress = count
                progressTintList = android.content.res.ColorStateList.valueOf(0xFFB22D54.toInt())
                contentDescription = "$title: $count đơn"
            }, LinearLayout.LayoutParams(-1, dp(8)))
        }
        dashboard.featureCard("Nhắc việc", "Theo dõi đơn và phiên live")
            .label("${data.content.count { it.status == "DRAFT" }} đơn nháp cần kiểm tra · ${data.content.count { it.status == "CONFIRMED" }} đơn chờ giao\nPhiên live đang chọn: ${cherri.tokens.liveSessionId()?.toString() ?: "Chưa chọn"}")
    }

    private fun createSystemSection() {
        val card = body.featureCard("Cài đặt & tài khoản", cherri.tokens.name())
        connectionBadge = card.label("● Đang kiểm tra kết nối…").apply {
            textSize = 14f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat(); setColor(0xFFF0F3F1.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12) }
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Server đang sử dụng")
                    .setMessage(cherri.tokens.baseUrl()).setPositiveButton("Đóng", null).show()
            }
            contentDescription = "Trạng thái kết nối. Nhấn để xem URL server"
        }
        card.action("Đổi URL server", "secondary") {
            val input = EditText(this).apply { setText(cherri.tokens.baseUrl()); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
            AlertDialog.Builder(this).setTitle("Đổi server và đăng nhập lại").setView(input)
                .setPositiveButton("Lưu") { _, _ ->
                    runCatching { cherri.tokens.setBaseUrl(input.text.toString()) }
                        .onSuccess { stopService(Intent(this, FloatingWindowService::class.java)); cherri.tokens.clear(); login() }
                        .onFailure { toast(errorText(it)) }
                }.setNegativeButton("Đóng", null).show()
        }
        card.label("Kết nối Database, Ngrok và Webhook được cấu hình trên server.").textSize = 13f
        val controls = card.actionRow()
        overlaySwitch = controls.toggle("Bật/Tắt Nút Nổi")
        controls.action("⚙", "secondary") { accessibilitySettings() }.apply {
            contentDescription = "Cấu hình đọc comment TikTok"
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(52))
        }
        overlaySwitch.setOnCheckedChangeListener { _, enabled -> if (!syncingOverlay) setOverlay(enabled) }
        card.action("Cài đặt in đơn · A4 / A5", "secondary") {
            val preferences = getSharedPreferences("print_settings", 0)
            val sizes = arrayOf("A4", "A5")
            AlertDialog.Builder(this).setTitle("Khổ giấy mặc định")
                .setSingleChoiceItems(sizes, if (preferences.getString("paper", "A4") == "A5") 1 else 0) { dialog, index ->
                    preferences.edit().putString("paper", sizes[index]).apply(); dialog.dismiss()
                }.setNegativeButton("Đóng", null).show()
        }
        card.action("Đăng xuất", "secondary") {
            stopService(Intent(this, FloatingWindowService::class.java)); liveClient?.close(); cherri.tokens.clear(); login()
        }
    }

    private fun accessibilitySettings() {
        AlertDialog.Builder(this).setTitle("Cấu hình đọc comment TikTok")
            .setMessage("Đọc nội dung bạn bấm/chọn trên TikTok để điền đơn. Nếu không đọc được text, hãy nhập comment thủ công.")
            .setPositiveButton("Mở Cài đặt") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Đóng", null).show()
    }

    private fun createLiveSection() {
        val card = body.featureCard("Quản lý phiên Live", "Khai báo và kết nối phiên livestream")
        val liveTitle = card.field("Tên phiên live")
        val room = card.field("TikTok Room ID (nếu có)")
        val session = card.field("ID phiên live", cherri.tokens.liveSessionId()?.toString() ?: "", InputType.TYPE_CLASS_NUMBER)
        card.action("Tạo Phiên Live") { network {
            require(liveTitle.text.isNotBlank()) { "Nhập tên phiên live" }
            val result = cherri.api.service().createSession(LiveSessionRequest(liveTitle.text.toString().trim(), room.text.toString().trim().ifBlank { null }))
            liveClient?.close(); liveResults.removeAllViews()
            cherri.tokens.setLiveSessionId(result.id); session.setText(result.id.toString()); toast("Đã tạo phiên ${result.id}")
        } }
        card.action("Kết Nối Comment Gateway", "secondary") {
            val id = session.text.toString().toLongOrNull()?.takeIf { it > 0 }
            if (id == null) toast("Nhập ID phiên live hợp lệ")
            else { cherri.tokens.setLiveSessionId(id); connectLive(id) }
        }
        card.action("Kết Thúc Phiên Live", "danger") {
            val id = session.text.toString().toLongOrNull()?.takeIf { it > 0 }
            if (id == null) toast("Chưa chọn phiên live")
            else AlertDialog.Builder(this).setMessage("Kết thúc phiên live $id?")
                .setPositiveButton("Kết thúc") { _, _ -> network {
                    cherri.api.service().endSession(id)
                    cherri.tokens.setLiveSessionId(null); session.text.clear(); liveClient?.close()
                    liveResults.removeAllViews(); toast("Phiên live đã kết thúc")
                } }.setNegativeButton("Đóng", null).show()
        }
        card.label("Comment từ gateway · Nhấn comment để chốt")
        liveResults = column().apply { setPadding(0, 0, 0, 0) }
        card.addView(NestedScrollView(this).apply { addView(liveResults) }, LinearLayout.LayoutParams(-1, dp(160)))
    }

    private fun createCheckoutSection() {
        val card = body.featureCard("Chốt đơn thủ công & Live", "Thao tác nhanh trong phiên live")
        checkoutSwitch = card.toggle("Bật nút chốt đơn Live")
        checkoutSwitch.setOnCheckedChangeListener { _, enabled -> if (!syncingOverlay) setOverlay(enabled) }
        card.label("Nút chốt đơn Live dùng cùng nút nổi ở phần cấu hình.").textSize = 12f
        val comment = card.field("Nhập comment để chốt thủ công…", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        comment.minLines = 2
        val actions = card.actionRow()
        actions.action("Chốt Từ Comment") { showCheckout(comment.text.toString()) }
        actions.action("📷 Chốt Kèm Ảnh Bill / Comment Đã Lưu", "secondary") {
            manualComment = comment.text.toString(); photo.launch("image/*")
        }
        syncOverlay()
    }

    private fun createOrdersSection() {
        val card = body.featureCard("Quản lý đơn hàng & lịch sử", "Đơn gần đây · Nhấn vào đơn để cập nhật")
        phone = card.field("Lọc SĐT", type = InputType.TYPE_CLASS_PHONE)
        nick = card.field("Lọc TikTok ID")
        code = card.field("Lọc mã đơn")
        status = card.choice(listOf("TẤT CẢ", "DRAFT", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED"))
        channel = card.choice(listOf("TẤT CẢ", "TIKTOK", "FACEBOOK", "ZALO", "PHONE"))
        card.action("Tìm đơn / làm mới") { page = 0; loadOrders() }
        val pagination = card.actionRow()
        pagination.action("Trang trước", "secondary") { if (page > 0) { page--; loadOrders() } }
        pagination.action("Trang sau", "secondary") { if (page + 1 < totalPages) { page++; loadOrders() } }
        results = column().apply { setPadding(0, 0, 0, 0) }
        card.addView(NestedScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(-1, dp(360)))
        val catalog = body.featureCard("Sản phẩm & tồn kho", "Tra cứu mã hàng và giá bán")
        val productResults = column().apply { setPadding(0, 0, 0, 0) }
        catalog.action("Tải danh mục sản phẩm", "secondary") { network {
            val products = cherri.api.service().products()
            productResults.removeAllViews()
            productResults.label("${products.totalElements} sản phẩm · Hiển thị tối đa 100 mã")
            products.content.forEach { product ->
                productResults.featureCard(product.shortCode + " · " + product.name, product.status)
                    .label("${money(product.sellingPrice)} · Tồn kho ${product.stockQuantity}")
            }
            if (products.content.isEmpty()) productResults.label("Chưa có sản phẩm.")
        } }
        catalog.addView(productResults)
    }

    private fun setOverlay(enabled: Boolean) {
        if (!enabled) { enableAfterPermission = false; stopService(Intent(this, FloatingWindowService::class.java)); syncOverlay(); return }
        if (!Settings.canDrawOverlays(this)) {
            enableAfterPermission = true
            overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            syncOverlay()
        } else startOverlay()
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        ContextCompat.startForegroundService(this, Intent(this, FloatingWindowService::class.java))
        toast("Mở TikTok, chọn comment rồi bấm nút nổi để chốt đơn.")
    }

    private fun syncOverlay() {
        if (!::overlaySwitch.isInitialized || !::checkoutSwitch.isInitialized) return
        syncingOverlay = true
        overlaySwitch.isChecked = FloatingWindowService.isRunning
        checkoutSwitch.isChecked = FloatingWindowService.isRunning
        syncingOverlay = false
    }

    private fun showCheckout(comment: String, file: File? = null) {
        QuickOrderBottomSheet(this, lifecycleScope, file, comment, onDismiss = { page = 0; loadOrders() }).show()
    }

    private fun connection(connected: Boolean) {
        connectionBadge.text = if (connected) "● Connected" else "● Không kết nối được"
        connectionBadge.setTextColor(if (connected) 0xFF237A45.toInt() else 0xFFB3261E.toInt())
    }

    override fun onResume() {
        super.onResume()
        syncOverlay()
        if (::results.isInitialized && cherri.tokens.token() != null) { loadOrders(); if (selectedTab == 1) loadDashboard() }
    }

    private fun connectLive(id: Long) {
        liveClient?.close()
        liveClient = LiveCommentClient(cherri.api, cherri.tokens, id,
            onComment = { event -> runOnUiThread {
                if (!isFinishing) {
                    if (liveResults.childCount >= 20) liveResults.removeViewAt(0)
                    liveResults.button("${event.tiktokId ?: "Khách"}: ${event.comment}") {
                        val raw = (event.tiktokId?.let { "@$it " } ?: "") + event.comment
                        showCheckout(raw)
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
        connection(true)
        totalPages = data.totalPages.coerceAtLeast(1)
        results.removeAllViews()
        results.label("Trang ${data.page + 1}/${data.totalPages.coerceAtLeast(1)} · ${data.totalElements} đơn")
        val headings = results.actionRow()
        listOf("Tên khách hàng", "Mã đơn", "Trạng thái").forEach { value ->
            headings.label(value).apply { textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        }
        if (data.content.isEmpty()) results.label("Chưa có đơn phù hợp.")
        data.content.forEach { order ->
            val row = results.actionRow().apply {
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setBackgroundColor(if (results.childCount % 2 == 0) 0xFFFAF5F7.toInt() else android.graphics.Color.WHITE)
                minimumHeight = dp(56)
                isClickable = true; isFocusable = true
                contentDescription = "${order.customer.name}, ${order.orderCode}, ${order.status}. Nhấn để quản lý đơn"
                setOnClickListener { manageOrder(order) }
            }
            listOf(order.customer.name, order.orderCode, order.status).forEach { value ->
                row.label(value).apply { textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            }
        }
    }

    private fun manageOrder(order: OrderView) {
        AlertDialog.Builder(this).setTitle(order.orderCode).setItems(arrayOf("Đổi trạng thái", "Ghi nhận thanh toán", "Mã vận đơn", "In đơn / Lưu PDF")) { _, option ->
            when (option) {
                3 -> printOrder(order)
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
        catch (e: Exception) { if (e is java.io.IOException || (e is retrofit2.HttpException && e.code() == 401)) connection(false); toast(errorText(e)); if (cherri.tokens.token() == null) login() }
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
    override fun onDestroy() { if (receiverRegistered) unregisterReceiver(overlayReceiver); liveClient?.close(); super.onDestroy() }
}
