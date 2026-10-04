package com.cherri.diary.android.ui

import android.Manifest
import android.view.View
import android.widget.FrameLayout
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cherri.diary.android.ui.design.*
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
    private var selectedTab by androidx.compose.runtime.mutableStateOf(1)
    private lateinit var results: LinearLayout
    private lateinit var phone: EditText
    private lateinit var nick: EditText
    private lateinit var code: EditText
    private lateinit var status: Spinner
    private lateinit var channel: Spinner
    private var page = 0
    private var totalPages = 1
    private var liveClient: LiveCommentClient? = null
    private val liveComments = androidx.compose.runtime.mutableStateListOf<ReceivedComment>()
    private var liveStatus by androidx.compose.runtime.mutableStateOf("Chưa kết nối")
    private var liveUsername by androidx.compose.runtime.mutableStateOf("")
    private var receivingComments by androidx.compose.runtime.mutableStateOf(false)
    private var receivedSequence = 0L
    private var manualComment = ""



    private var commentCount by androidx.compose.runtime.mutableStateOf(0)
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
            setBackgroundColor(0xFFF8F9FA.toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        val header = column().apply { setPadding(dp(20), dp(12), dp(20), dp(4)) }
        header.label("Cherri Diary").apply { textSize = 24f; setTextColor(0xFFD81B60.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        root.addView(header)
        val container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        repeat(4) {
            body = column().apply { setPadding(dp(16), dp(12), dp(16), dp(24)) }
            val scroll: View = if (it == 0 || it == 1) body.apply { visibility = View.GONE } else NestedScrollView(this).apply { addView(body); visibility = View.GONE }
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
        selectedTab = savedInstanceState?.getInt("tab", 1)?.coerceIn(1, 4) ?: 1
        root.removeView(header)
        root.removeView(container)
        setContent {
            CherriTheme {
                Scaffold(bottomBar = {
                    BottomNavigationBar(CherriTab.entries[selectedTab - 1]) { tab ->
                        selectedTab = tab.ordinal + 1
                        tabPages.forEachIndexed { index, view -> view.visibility = if (index + 1 == selectedTab) View.VISIBLE else View.GONE }
                        if (selectedTab == 1) loadDashboard()
                        if (selectedTab == 3) loadOrders()
                    }
                }) { padding ->
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding)) {
                        AndroidView(factory = { header }, modifier = Modifier.fillMaxWidth())
                        AndroidView(factory = { container }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        tabPages[selectedTab - 1].visibility = View.VISIBLE
        loadDashboard()
        ContextCompat.registerReceiver(this, overlayReceiver, IntentFilter(FloatingWindowService.STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    private var dashboardOrders by androidx.compose.runtime.mutableStateOf<List<OrderView>>(emptyList())
    private var dashboardTotal by androidx.compose.runtime.mutableStateOf(0L)
    private var dashboardLoading by androidx.compose.runtime.mutableStateOf(true)

    private fun createDashboard() {
        dashboard.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CherriTheme {
                CherriDashboard(cherri.tokens.name(), dashboardOrders, dashboardTotal, dashboardLoading) { loadDashboard() }
            } }
        }, LinearLayout.LayoutParams(-1, -1))
    }

    private fun loadDashboard(): Unit = network {
        dashboardLoading = true
        try {
            val data = cherri.api.service().orders(size = 100)
            connection(true)
            dashboardOrders = data.content
            dashboardTotal = data.totalElements
        } finally { dashboardLoading = false }
    }
    private fun createSystemSection() {
        body.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CherriTheme {
                    com.cherri.diary.android.ui.management.ManagementMenu { route ->
                        startActivity(Intent(this@MainActivity, com.cherri.diary.android.ui.management.ManagementActivity::class.java).putExtra("route", route))
                    }
                }
            }
        })
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
        body.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CherriTheme {
                LiveCommentsScreen(liveComments.toList(), commentCount, liveStatus, receivingComments, liveUsername,
                    connect = { value, selectedId -> network {
                        require(value.isNotBlank()) { "Nhập @username TikTok" }
                        liveStatus = "Đang kết nối…"; receivingComments = false
                        val id = selectedId ?: cherri.api.service().createSession(LiveSessionRequest("Live $value", null)).id
                        cherri.api.service().selectLiveSource(LiveSourceRequest(id, value))
                        cherri.tokens.setLiveSessionId(id); liveUsername = value
                        liveComments.clear(); commentCount = 0; connectLive(id)
                    } }, end = {
                        val id = cherri.tokens.liveSessionId()
                        if (id == null) toast("Chưa có phiên live")
                        else AlertDialog.Builder(this@MainActivity).setMessage("Kết thúc phiên #$id?").setPositiveButton("Kết thúc") { _, _ -> network {
                            cherri.api.service().endSession(id); cherri.tokens.setLiveSessionId(null); liveClient?.close()
                            liveStatus = "Phiên đã kết thúc"; receivingComments = false
                        } }.setNegativeButton("Đóng", null).show()
                    }, checkout = { event -> showCheckout(event.comment, customerName = event.nickname ?: event.tiktokId, tiktokId = event.tiktokId) })
            } }
        })
        network {
            val selected = cherri.api.service().liveSource()
            liveUsername = selected.username?.let { "@$it" } ?: ""
            if (selected.enabled && selected.liveSessionId != null) {
                cherri.tokens.setLiveSessionId(selected.liveSessionId); connectLive(selected.liveSessionId)
            }
        }
    }
    private fun createCheckoutSection() {
        val actions = body.actionRow()
        checkoutSwitch = actions.toggle("Nút nổi")
        checkoutSwitch.setOnCheckedChangeListener { _, enabled -> if (!syncingOverlay) setOverlay(enabled) }
        actions.action("Chốt thủ công", "secondary") { showCheckout("") }
        actions.action("Ảnh bill", "secondary") { manualComment = ""; photo.launch("image/*") }
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

    private fun showCheckout(comment: String, file: File? = null, customerName: String? = null, tiktokId: String? = null) {
        QuickOrderBottomSheet(this, lifecycleScope, file, comment, onDismiss = { page = 0; loadOrders() }, initialCustomerName = customerName, initialTiktokId = tiktokId).show()
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
                if (!isFinishing && cherri.tokens.liveSessionId() == event.liveSessionId) {
                    commentCount++
                    liveStatus = "● Đang nhận comment · Phiên #$id"
                    receivingComments = true
                    liveComments.add(0, ReceivedComment(++receivedSequence, event))
                    if (liveComments.size > 200) liveComments.removeAt(liveComments.lastIndex)
                }
            } }, onError = { message -> runOnUiThread {
                liveStatus = message; receivingComments = false
            } })
        liveClient?.connect()
        liveStatus = "Đang kết nối gateway · Phiên #$id"
    }    private fun loadOrders() = network {
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
        val options = arrayOf("Đổi trạng thái", "Ghi nhận thanh toán", "Mã vận đơn", "In đơn / Lưu PDF") + if (order.status == "DRAFT" && order.items.isEmpty()) arrayOf("Bổ sung mã hàng cho đơn nháp") else emptyArray()
        AlertDialog.Builder(this).setTitle(order.orderCode).setItems(options) { _, option ->
            when (option) {
                4 -> {
                    val form = column()
                    form.label("${order.customer.name}\nComment: ${order.commentRaw ?: "Không có"}")
                    val lines = form.field("Mã và số lượng: A1 2, V2 1")
                    val price = form.field("Giá bán cho mã mới (đ, tùy chọn)", type = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                    form.label("Mã đã có dùng giá server. Với nhiều mã mới khác giá, hãy tạo trong Sản phẩm trước.")
                    val sheet = AlertDialog.Builder(this).setTitle("Bổ sung hàng · ${order.orderCode}").setView(form).setPositiveButton("Lưu", null).setNegativeButton("Đóng", null).create()
                    sheet.setOnShowListener {
                        sheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                        sheet.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { network {
                            val parsed = lines.text.toString().split(',').map { line ->
                                val match = Regex("^\\s*([A-Za-z][A-Za-z0-9_-]{0,29})(?:\\s+(\\d+))?\\s*$").matchEntire(line) ?: error("Nhập dạng A1 2, V2 1")
                                QuickOrderItem(match.groupValues[1], match.groupValues[2].ifBlank { "1" }.toInt(), price.text.toString().takeIf { it.isNotBlank() }?.toBigDecimal())
                            }
                            val button = sheet.getButton(AlertDialog.BUTTON_POSITIVE); button.isEnabled = false
                            try { cherri.api.service().draftItems(order.id, DraftItemsRequest(parsed)); sheet.dismiss(); loadOrders(); toast("Đã bổ sung hàng. Có thể xác nhận đơn nháp.") }
                            finally { button.isEnabled = true }
                        } }
                    }
                    sheet.show()
                }
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
