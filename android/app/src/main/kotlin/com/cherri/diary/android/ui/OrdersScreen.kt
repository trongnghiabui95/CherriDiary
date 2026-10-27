package com.cherri.diary.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherri.diary.android.R
import com.cherri.diary.android.data.*
import com.cherri.diary.android.ui.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

data class OrderFilters(val query: String = "", val status: String? = null,
    val from: String? = null, val to: String? = null, val grouped: Boolean = false, val page: Int = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(orders: List<OrderView>, groups: List<GroupedOrdersView>, loading: Boolean, error: String?,
    pageCount: Int, total: Long, refreshVersion: Int, load: (OrderFilters) -> Unit,
    manage: (OrderView) -> Unit, pack: suspend (PackOrdersRequest) -> Unit, products: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    var grouped by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var filterError by remember { mutableStateOf<String?>(null) }
    var packing by remember { mutableStateOf<GroupedOrdersView?>(null) }
    var chosenGroup by remember { mutableStateOf<GroupedOrdersView?>(null) }
    var tracking by remember { mutableStateOf("") }
    var packingBusy by remember { mutableStateOf(false) }
    var packingError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun filters(): OrderFilters {
        val zone = ZoneId.systemDefault()
        return OrderFilters(query.trim(), selectedStatus,
            fromDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toString() },
            toDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toString() }, grouped, page)
    }
    LaunchedEffect(query, selectedStatus, fromDate, toDate, grouped, page, refreshVersion) {
        delay(350)
        load(filters())
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it; page = 0 }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Tìm theo Tên, SĐT, TikTok ID hoặc Mã đơn…", style = MaterialTheme.typography.bodySmall) },
                shape = RoundedCornerShape(16.dp), trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; page = 0 }) { Text("×") }
                })
            FilledTonalButton(onClick = { filterOpen = true }, contentPadding = PaddingValues(12.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(painterResource(R.drawable.ic_filter), null, Modifier.size(20.dp)); Text("Lọc")
            }
        }
        TabRow(selectedTabIndex = if (grouped) 1 else 0) {
            Tab(selected = !grouped, onClick = { grouped = false; page = 0 }, text = { Text("Theo Đơn") })
            Tab(selected = grouped, onClick = { grouped = true; page = 0 }, text = { Text("Gom Theo Khách") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$total ${if (grouped) "nhóm khách" else "đơn hàng"}", Modifier.weight(1f), color = CherriColors.Subtitle)
            TextButton(onClick = { load(filters()) }, enabled = !loading) { Text("Làm mới") }
            TextButton(onClick = products) { Text("Sản phẩm") }
        }
        if (grouped) Text("Gom toàn bộ đơn chưa giao của khách phù hợp bộ lọc.", style = MaterialTheme.typography.bodySmall,
            color = CherriColors.Subtitle)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (loading) items(3) { CommentSkeleton() }
            else if (grouped) {
                items(groups, key = { it.orders.first().id }) { group ->
                    OrderListCard(group.orders, true, onOpen = { chosenGroup = group },
                        onPack = { packing = group; tracking = ""; packingError = null })
                }
            } else items(orders, key = { it.id }) { order -> OrderListCard(listOf(order), false, { manage(order) }) }
            if (!loading && error == null && (if (grouped) groups.isEmpty() else orders.isEmpty())) item {
                Text("Chưa có đơn phù hợp. Thử đổi từ khóa hoặc bộ lọc.", Modifier.padding(16.dp), color = CherriColors.Subtitle)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { page-- }, enabled = !loading && page > 0) { Text("Trang trước") }
            Text("${page + 1}/${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { page++ }, enabled = !loading && page + 1 < pageCount) { Text("Trang sau") }
        }
    }
    chosenGroup?.let { group ->
        AlertDialog(onDismissRequest = { chosenGroup = null }, title = { Text("Đơn của ${group.customer.name}") },
            text = { LazyColumn { items(group.orders, key = { it.id }) { order ->
                TextButton(onClick = { chosenGroup = null; manage(order) }) {
                    Text("${order.orderCode} · ${orderStatusLabel(order.status)}")
                }
            } } }, confirmButton = { TextButton(onClick = { chosenGroup = null }) { Text("Đóng") } })
    }
    if (filterOpen) {
        var draftStatus by remember { mutableStateOf(selectedStatus) }
        var draftFrom by remember { mutableStateOf(fromDate) }
        var draftTo by remember { mutableStateOf(toDate) }
        ModalBottomSheet(onDismissRequest = { filterOpen = false }) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Lọc đơn hàng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(listOf(null, "DRAFT", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED")) { status ->
                    Row(Modifier.fillMaxWidth().clickable { draftStatus = status }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draftStatus == status, { draftStatus = status })
                        Text(status?.let(::orderStatusLabel) ?: "Tất cả trạng thái")
                    }
                }
                item { OutlinedTextField(draftFrom, { draftFrom = it }, label = { Text("Từ ngày (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draftTo, { draftTo = it }, label = { Text("Đến ngày (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { val today = LocalDate.now(); draftFrom = today.toString(); draftTo = today.toString() }) { Text("Hôm nay") }
                        TextButton(onClick = { val today = LocalDate.now(); draftFrom = today.minusDays(6).toString(); draftTo = today.toString() }) { Text("7 ngày") }
                        TextButton(onClick = { draftStatus = null; draftFrom = ""; draftTo = "" }) { Text("Xóa lọc") }
                    }
                }
                if (filterError != null) item { Text(filterError!!, color = MaterialTheme.colorScheme.error) }
                item { Button(onClick = {
                    try {
                        val first = draftFrom.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                        val last = draftTo.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                        require(first == null || last == null || !first.isAfter(last))
                        selectedStatus = draftStatus; fromDate = draftFrom; toDate = draftTo; page = 0
                        filterError = null; filterOpen = false
                    } catch (_: Exception) { filterError = "Nhập ngày yyyy-MM-dd; ngày bắt đầu không được sau ngày kết thúc." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Áp dụng") } }
            }
        }
    }
    packing?.let { group ->
        AlertDialog(onDismissRequest = { if (!packingBusy) packing = null }, title = { Text("Gộp & Tạo vận đơn") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${group.customer.name} · ${group.orders.size} đơn. Nhập mã vận đơn đã tạo tại hãng để đóng gói chung. Các đơn gốc được giữ nguyên.")
                OutlinedTextField(tracking, { tracking = it }, label = { Text("Mã vận đơn chung") }, singleLine = true, enabled = !packingBusy)
                if (packingError != null) Text(packingError!!, color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(enabled = !packingBusy && tracking.isNotBlank(), onClick = {
                packingBusy = true
                scope.launch {
                    try { pack(PackOrdersRequest(group.orders.map { it.id }, tracking)); packing = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { packingError = errorText(e) }
                    finally { packingBusy = false }
                }
            }) { Text(if (packingBusy) "Đang lưu…" else "Gộp & lưu vận đơn") } },
            dismissButton = { TextButton(enabled = !packingBusy, onClick = { packing = null }) { Text("Đóng") } })
    }
}

private fun orderStatusLabel(status: String) = when (status) {
    "DRAFT" -> "Nháp"; "CONFIRMED" -> "Chờ đi đơn"; "SHIPPING" -> "Đang giao"
    "COMPLETED" -> "Hoàn tất"; "CANCELLED" -> "Đã hủy"; else -> status
}

@Composable
private fun OrderListCard(orders: List<OrderView>, grouped: Boolean, onOpen: () -> Unit, onPack: (() -> Unit)? = null) {
    val first = orders.first()
    val packed = orders.all { it.status == "CONFIRMED" && !it.trackingCode.isNullOrBlank() }
    val badge = if (grouped) "${orders.size} đơn chưa giao" else if (packed) "Đã đóng gói" else orderStatusLabel(first.status)
    val background = when { packed -> Color(0xFFE3F2FD); grouped -> Color(0xFFFFF0DD); first.status in listOf("DRAFT", "CANCELLED") -> Color(0xFFEEEEEE); first.status == "COMPLETED" -> Color(0xFFE2F2E8); else -> Color(0xFFFFF0DD) }
    val foreground = when { packed -> Color(0xFF1565C0); grouped -> Color(0xFF805000); first.status in listOf("DRAFT", "CANCELLED") -> CherriColors.Subtitle; first.status == "COMPLETED" -> Color(0xFF246544); else -> Color(0xFF805000) }
    val lines = orders.flatMap { it.items }.groupBy { it.shortCode }.map { (_, items) ->
        "${items.first().productName?.takeIf(String::isNotBlank) ?: items.first().shortCode} x${items.sumOf { it.quantity.toLong() }}"
    }
    val amount = orders.fold(BigDecimal.ZERO) { total, order -> total + order.subtotalAmount + order.shippingFee }
    val quantity = orders.flatMap { it.items }.sumOf { it.quantity.toLong() }
    val time = first.createdAt?.let { runCatching { DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy")
        .withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrNull() } ?: "Chưa có thời gian"
    Card(onClick = onOpen, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(first.customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(first.customer.tiktokId?.let { "@${it.removePrefix("@")}" } ?: first.customer.phoneNumber ?: "Chưa có liên hệ",
                    Modifier.weight(1f), color = CherriColors.Subtitle, style = MaterialTheme.typography.bodySmall)
                Surface(color = background, shape = RoundedCornerShape(16.dp)) {
                    Text(badge, Modifier.padding(8.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(if (lines.isEmpty()) "Đơn nháp chưa bổ sung sản phẩm" else lines.joinToString(" · "))
            Text(if (grouped) orders.joinToString(" · ") { it.orderCode } else first.orderCode, style = MaterialTheme.typography.bodySmall,
                color = CherriColors.Subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("$quantity món · ${money(amount)}", fontWeight = FontWeight.Bold, color = CherriColors.Cherry)
            Text(if (grouped) "Chốt gần nhất: $time" else time, style = MaterialTheme.typography.bodySmall, color = CherriColors.Subtitle)
            if (packed) Text("Vận đơn: ${orders.mapNotNull { it.trackingCode }.distinct().joinToString()}", style = MaterialTheme.typography.bodySmall)
            if (onPack != null) {
                Button(onClick = onPack, enabled = orders.size <= 100 && orders.all { it.items.isNotEmpty() && it.trackingCode.isNullOrBlank() },
                    modifier = Modifier.fillMaxWidth()) { Text(if (packed) "Đã đóng gói" else "Gộp & Tạo vận đơn") }
                if (orders.any { it.items.isEmpty() }) Text("Bổ sung sản phẩm cho đơn nháp trước khi gom.", color = CherriColors.Subtitle, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpen) { Text("Xem ${orders.size} đơn gốc") }
            }
        }
    }
}
