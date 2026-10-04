package com.cherri.diary.android.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Standalone interactive design sample. Data here is illustrative, not API data. */
@Composable
fun CherriSampleApp() {
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(CherriTab.Overview) }
    var loading by remember { mutableStateOf(false) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var secondConfirmed by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var recovery by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == CherriTab.Live) { loading = true; delay(1200); loading = false }
    }
    CherriTheme {
        if (!loggedIn) CherriLoginScreen("https://example.com/api/v1/", false, false, null,
            onLogin = { _, _, _, _ -> loggedIn = true }, onForgotPassword = { recovery = true })
        else Scaffold(bottomBar = { BottomNavigationBar(tab) { tab = it } }) { padding ->
            Crossfade(tab, modifier = Modifier.fillMaxSize().padding(padding), label = "tab transition") { selected ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(when (selected) {
                        CherriTab.Overview -> "Tổng quan hôm nay"
                        CherriTab.Live -> "Live Comment"
                        CherriTab.Orders -> "Đơn hàng & sản phẩm"
                        CherriTab.Settings -> "Cài đặt hệ thống"
                    }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    when (selected) {
                        CherriTab.Overview -> {
                            item { SampleMetric("Doanh thu minh họa", "12.450.000 ₫") }
                            item { SampleMetric("Đơn đã chốt", "38 đơn") }
                            item { Text("Dữ liệu minh họa để xem thiết kế", color = CherriColors.Subtitle) }
                        }
                        CherriTab.Live -> {
                            if (loading) items(3) { CommentSkeleton() }
                            else {
                                item { CommentCard("Linh Nguyễn", "@linh.nguyen", "0987654321 A1 2", "20:32",
                                    if (confirmed) CommentStatus.Confirmed else CommentStatus.New, { confirmed = true }) }
                                item { CommentCard("Mai Anh", "@mai.anh", "Chốt em áo A2 màu hồng nhé", "20:33",
                                    if (secondConfirmed) CommentStatus.Confirmed else CommentStatus.MissingPhone, { secondConfirmed = true }) }
                            }
                        }
                        CherriTab.Orders -> {
                            item { SampleMetric("Đơn #CH001 · Linh Nguyễn", "450.000 ₫ · Đã chốt") }
                            item { SampleMetric("Áo Cherry · A1", "225.000 ₫ · Còn 24 sản phẩm") }
                        }
                        CherriTab.Settings -> {
                            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Thông báo", Modifier.weight(1f))
                                Switch(notifications, { notifications = it })
                            } }
                            item { OutlinedButton(onClick = { loggedIn = false }) { Text("Đăng xuất") } }
                        }
                    }
                }
            }
        }
        if (recovery) AlertDialog(onDismissRequest = { recovery = false }, title = { Text("Quên mật khẩu") },
            text = { Text("Liên hệ quản trị viên để cấp lại mật khẩu.") },
            confirmButton = { TextButton(onClick = { recovery = false }) { Text("Đóng") } })
    }
}

@Composable
private fun SampleMetric(title: String, value: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CherriColors.Cream)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = CherriColors.Subtitle)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = CherriColors.Raspberry, fontWeight = FontWeight.Bold)
        }
    }
}
