package com.cherri.diary.android.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cherri.diary.android.data.OrderView
import com.cherri.diary.android.ui.money
import java.math.BigDecimal

@Composable
fun CherriDashboard(name: String, orders: List<OrderView>, total: Long, loading: Boolean, onRefresh: () -> Unit) {
    val active = orders.filter { it.status != "CANCELLED" }
    val value = active.fold(BigDecimal.ZERO) { sum, order -> sum + order.totalAmount }
    val paid = active.fold(BigDecimal.ZERO) { sum, order -> sum + order.depositAmount + order.paidAmount }
    val remaining = active.fold(BigDecimal.ZERO) { sum, order -> sum + order.remainingAmount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Xin chào, $name", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Cùng Cherri chăm sóc từng đơn hàng", color = CherriColors.Subtitle)
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(CherriColors.Raspberry, Color(0xFFAF174D))))
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("GIÁ TRỊ ĐƠN GẦN ĐÂY", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text(if (loading) "Đang tải…" else money(value), color = Color.White,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${orders.size} đơn gần đây · $total đơn trong hệ thống", color = Color.White,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { MetricCard("Đã thu", money(paid), "Tiền cọc và thanh toán của các đơn gần đây") }
        item { MetricCard("Còn thu", money(remaining), "Theo dõi thanh toán để không bỏ sót đơn") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CherriColors.Cream), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Trạng thái đơn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    linkedMapOf("DRAFT" to "Nháp", "CONFIRMED" to "Đã chốt", "SHIPPING" to "Đang giao",
                        "COMPLETED" to "Hoàn tất", "CANCELLED" to "Đã hủy").forEach { (key, title) ->
                        val count = orders.count { it.status == key }
                        Text("$title · $count", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { count.toFloat() / orders.size.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.primaryContainer)
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Làm mới báo cáo") } }
    }
}

@Composable
private fun MetricCard(title: String, amount: String, caption: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = CherriColors.Subtitle)
            Text(amount, style = MaterialTheme.typography.headlineSmall, color = CherriColors.Raspberry, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = CherriColors.Subtitle)
        }
    }
}
