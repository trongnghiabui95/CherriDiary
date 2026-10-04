package com.cherri.diary.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherri.diary.android.data.LiveComment
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ReceivedComment(val key: Long, val event: LiveComment, val receivedAt: Instant = Instant.now())

@Composable fun LiveCommentsScreen(comments: List<ReceivedComment>, count: Int, status: String, receiving: Boolean,
    initialUsername: String, connect: (String, Long?) -> Unit, end: () -> Unit, checkout: (LiveComment) -> Unit) {
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    LaunchedEffect(initialUsername) { if (initialUsername.isNotBlank()) username = initialUsername }
    var expanded by rememberSaveable { mutableStateOf(true) }
    var sessionId by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(receiving) { if (receiving) expanded = false }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showTop by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    // Stable item keys preserve the visible comment when new entries arrive above it.
    // At the newest entry, explicitly stay at index 0 to follow the live feed.
    var followNewest by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { !list.isScrollInProgress && list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }
            .collect { followNewest = it }
    }
    LaunchedEffect(comments.firstOrNull()?.key) { if (followNewest && !list.isScrollInProgress) list.scrollToItem(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TikTok LIVE", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(status, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (expanded) "Thu gọn" else "Cấu hình", fontSize = 12.sp) }
                }
                if (expanded) {
                    OutlinedTextField(username, { username = it }, label = { Text("@username hoặc link TikTok Live") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { advanced = !advanced }) { Text("ID phiên Cherri (tùy chọn)", fontSize = 11.sp) }
                    if (advanced) OutlinedTextField(sessionId, { sessionId = it.filter(Char::isDigit) }, label = { Text("ID phiên; để trống để tạo mới") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { connect(username.trim(), sessionId.toLongOrNull()?.takeIf { it > 0 }) }, enabled = username.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Kết nối") }
                        OutlinedButton(onClick = end) { Text("Kết thúc") }
                    }
                }
            }
        }
        Text("$count comment nhận được · Giữ tối đa 200 comment", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (comments.isEmpty()) Text("Chưa có comment. Tài khoản phải đang LIVE và connector đang chạy.", fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 64.dp)) {
                items(comments, key = { it.key }) { received ->
                    val event = received.event
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(event.nickname?.takeIf { it.isNotBlank() } ?: event.tiktokId ?: "Khách TikTok", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${event.tiktokId?.let { "@${it.removePrefix("@")}" } ?: "TikTok"} · ${formatter.format(received.receivedAt)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(event.comment, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { checkout(event) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("Chốt đơn", fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (showTop) SmallFloatingActionButton(onClick = { scope.launch { followNewest = true; list.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                Text("↑ Comment mới nhất", modifier = Modifier.padding(horizontal = 12.dp), fontSize = 12.sp)
            }
        }
    }
}
