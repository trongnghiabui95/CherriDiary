package com.cherri.diary.android.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.text.InputType
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import com.cherri.diary.android.cherri
import com.cherri.diary.android.data.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.math.BigDecimal
import java.util.UUID

class QuickOrderBottomSheet(private val context: Context, parentScope: CoroutineScope,
    private val screenshot: File? = null, commentRaw: String = "", overlay: Boolean = false,
    private val onDismiss: () -> Unit = {}, private val initialCustomerName: String? = null, private val initialTiktokId: String? = null) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val dialog = BottomSheetDialog(context)
    private val body = context.column()
    private val orderCard = body.featureCard("Chốt đơn nhanh", "Tên khách và mã hàng · các thông tin khác có thể bổ sung sau")
    private val customerName = orderCard.field("Tên khách hàng", initialCustomerName ?: "")
    private val items = orderCard.field("Mã hàng · ví dụ A1 2, V2 1")
    private val commentCard = body.featureCard("Comment đã chọn", "Nội dung từ phiên live")
    private val comment = commentCard.field("Comment gốc", commentRaw, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val details = context.column().apply { visibility = android.view.View.GONE }.also(body::addView)
    private val phone = details.field("Số điện thoại (có thể bổ sung sau)", type = InputType.TYPE_CLASS_PHONE)
    private val nick = details.field("TikTok ID", initialTiktokId ?: "")
    private val facebook = details.field("Facebook ID (nếu có)")
    private val address = details.field("Địa chỉ giao hàng")
    private val channel = details.choice(listOf("TIKTOK", "FACEBOOK", "ZALO", "PHONE"))
    private val deposit = details.field("Tiền cọc", "0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val shipping = details.field("Phí ship", "0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val method = details.choice(listOf("COD", "BANK_TRANSFER"))
    private val blacklist = CheckBox(context).apply {
        text = "Khách trong danh sách đen · Tôi xác nhận tiếp tục chốt"
        visibility = android.view.View.GONE
    }.also(body::addView)
    private val summary = body.label("Chọn mã hàng để chốt đơn. Không bắt buộc SĐT khi có TikTok ID.")
    private var products = emptyMap<String, ProductView>()
    private var pendingRequest: FastCreateRequest? = null
    private var lastBlacklist = false
    private var busy = false
    private val submit: android.widget.Button
    private val quote: android.widget.Button
    private val parse: android.widget.Button

    init {
        orderCard.action("Chọn sản phẩm", "secondary") {
            if (!busy && pendingRequest == null) scope.launch {
                setBusy(true)
                try {
                    val available = context.cherri.api.service().products().content.filter { it.status == "ACTIVE" && it.stockQuantity > 0 }
                    if (available.isEmpty()) { summary.text = "Chưa có sản phẩm đang bán và còn kho" }
                    else androidx.appcompat.app.AlertDialog.Builder(context).setTitle("Chọn mã hàng · Số lượng mặc định 1")
                        .setItems(available.map { "${it.shortCode} · ${it.name} · ${money(it.sellingPrice)}" }.toTypedArray()) { _, index ->
                            val product = available[index]
                            products = products + (product.shortCode to product)
                            items.setText("${product.shortCode} 1"); refreshSummary()
                        }.setNegativeButton("Đóng", null).create().apply {
                            if (overlay) window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                        }.show()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { summary.text = errorText(e) }
                finally { setBusy(false) }
            }
        }
        orderCard.action("Thông tin giao hàng / cọc / tùy chọn", "secondary") {
            details.visibility = if (details.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }
        if (screenshot?.isFile == true) {
            val bitmap = BitmapFactory.decodeFile(screenshot.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
            body.addView(ImageView(context).apply { setImageBitmap(bitmap); adjustViewBounds = true; maxHeight = 320; contentDescription = "Ảnh bằng chứng comment" })
        }
        parse = details.button("Đọc lại comment") { analyze(comment.text.toString(), true) }
        quote = details.button("Cập nhật giá xem trước") { analyze(items.text.toString(), false) }
        submit = body.action("Chốt đơn ngay") { submit() }
        body.button("Đóng") { dialog.dismiss() }
        listOf(items, deposit, shipping).forEach { it.doAfterTextChanged { refreshSummary() } }
        dialog.setContentView(ScrollView(context).apply { addView(body) })
        if (overlay) dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener { scope.cancel(); screenshot?.delete(); onDismiss() }
        scope.coroutineContext[Job]!!.invokeOnCompletion { Handler(Looper.getMainLooper()).post { dialog.dismiss() } }
    }

    fun show() { dialog.show(); if (comment.text.isNotBlank()) analyze(comment.text.toString(), true) }
    fun dismiss() { dialog.dismiss() }

    private fun analyze(value: String, fillCustomer: Boolean) {
        if (busy || pendingRequest != null) return
        if (value.isBlank()) { summary.text = "Nhập comment hoặc mã hàng"; return }
        setBusy(true)
        scope.launch {
            try {
                val parseValue = if (fillCustomer && initialTiktokId != null) "@$initialTiktokId $value" else value
                val result = context.cherri.api.service().parseComment(ParseCommentRequest(parseValue))
                products = result.items.mapNotNull { it.product }.associateBy { it.shortCode }
                if (fillCustomer) {
                    phone.setText(result.phoneNumber ?: result.customer?.phoneNumber ?: "")
                    nick.setText(initialTiktokId ?: result.tiktokId ?: result.customer?.tiktokId ?: "")
                    customerName.setText(initialCustomerName ?: result.customer?.name ?: initialTiktokId ?: "")
                    address.setText(result.customer?.address ?: "")
                    items.setText(result.items.joinToString(", ") { "${it.shortCode} ${it.quantity}" })
                    lastBlacklist = result.isBlacklisted
                    blacklist.isChecked = false
                    blacklist.visibility = if (lastBlacklist) android.view.View.VISIBLE else android.view.View.GONE
                }
                refreshSummary()
                val warnings = result.warnings.filter { !it.startsWith("Chưa có SĐT") }
                if (warnings.isNotEmpty()) summary.append("\n" + warnings.joinToString("\n"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { summary.text = errorText(e) }
            finally { setBusy(false) }
        }
    }

    private fun selectedItems(): List<QuickOrderItem> = items.text.toString().split(Regex("[,;\\n]+")).map { line ->
        val match = Regex("^\\s*([A-Za-z][A-Za-z0-9_-]{0,29})(?:\\s+([0-9]+))?\\s*$").matchEntire(line)
            ?: error("Dùng định dạng A1 2, V2 1")
        val quantity = match.groupValues[2].ifBlank { "1" }.toIntOrNull()?.takeIf { it in 1..10000 } ?: error("Số lượng từ 1 đến 10000")
        QuickOrderItem(match.groupValues[1].uppercase(java.util.Locale.ROOT), quantity)
    }
    private fun amount(value: String): BigDecimal = value.trim().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it.scale() <= 2 }
        ?: error("Số tiền phải không âm và tối đa hai chữ số thập phân")
    private fun refreshSummary() {
        if (pendingRequest != null) return
        summary.text = runCatching {
            val selected = selectedItems()
            val subtotal = selected.fold(BigDecimal.ZERO) { total, item ->
                val product = products[item.shortCode] ?: error("Giá sẽ được backend cập nhật khi chốt · ${item.shortCode}")
                total + product.sellingPrice * item.quantity.toBigDecimal()
            }
            val cod = subtotal - amount(deposit.text.toString()) + amount(shipping.text.toString())
            require(cod.signum() >= 0) { "Tiền cọc vượt tổng đơn" }
            "Tiền hàng: ${money(subtotal)}\nCòn phải thu: ${money(cod)}" + if (lastBlacklist) "\n⚠ Khách trong danh sách đen" else ""
        }.getOrElse { it.message ?: "Kiểm tra lại dữ liệu" }
    }
    private fun submit() {
        if (busy) return
        try {
            if (pendingRequest == null) {
                val selected = selectedItems()
                // Backend validates product, stock and final price in the same transaction.
                require(!lastBlacklist || blacklist.isChecked) { "Cần xác nhận cảnh báo danh sách đen" }
                pendingRequest = FastCreateRequest(UUID.randomUUID().toString(), selected,
                    customerName = customerName.text.toString().ifBlank { null },
                    phoneNumber = phone.text.toString().ifBlank { null }, tiktokId = nick.text.toString().ifBlank { null },
                    facebookId = facebook.text.toString().ifBlank { null }, address = address.text.toString().ifBlank { null },
                    liveSessionId = context.cherri.tokens.liveSessionId(), channel = channel.selectedItem.toString(),
                    depositAmount = amount(deposit.text.toString()), shippingFee = amount(shipping.text.toString()),
                    paymentMethod = method.selectedItem.toString(), commentRaw = comment.text.toString().ifBlank { null }, acknowledgeBlacklist = blacklist.isChecked)
            }
            setBusy(true)
            setInputsEnabled(false)
            scope.launch {
                try {
                    val api = context.cherri.api
                    val requestBody = api.gson.toJson(pendingRequest).toRequestBody("application/json; charset=utf-8".toMediaType())
                    val proof = screenshot?.takeIf(File::isFile)?.let {
                        MultipartBody.Part.createFormData("proof", "comment.png", it.asRequestBody("image/png".toMediaType()))
                    }
                    val result = api.service().fastCreate(requestBody, proof)
                    context.toast("Đã tạo ${result.orderCode}. Còn phải thu ${money(result.remainingAmount)}")
                    dialog.dismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (e is HttpException && e.code() in 400..499) {
                        pendingRequest = null
                        setInputsEnabled(true)
                    }
                    val message = errorText(e)
                    if (message.contains("danh sách đen", ignoreCase = true)) {
                        lastBlacklist = true; blacklist.visibility = android.view.View.VISIBLE
                    }
                    if (message.contains("Cần SĐT") || message.contains("Thông tin liên hệ")) details.visibility = android.view.View.VISIBLE
                    summary.text = message + if (pendingRequest != null) "\nGửi lại sẽ dùng cùng mã yêu cầu và dữ liệu để tránh trùng đơn." else ""
                } finally {
                    setBusy(false)
                    submit.text = if (pendingRequest != null) "Gửi lại cùng đơn" else "Chốt đơn ngay"
                }
            }
        } catch (e: Exception) { summary.text = e.message; pendingRequest = null; setBusy(false) }
    }
    private fun setInputsEnabled(enabled: Boolean) {
        listOf(comment, customerName, phone, nick, facebook, address, channel, items, deposit, shipping, method, blacklist).forEach { it.isEnabled = enabled }
        parse.isEnabled = enabled; quote.isEnabled = enabled
    }
    private fun setBusy(value: Boolean) { busy = value; submit.isEnabled = !value; parse.isEnabled = !value && pendingRequest == null; quote.isEnabled = !value && pendingRequest == null }
}
