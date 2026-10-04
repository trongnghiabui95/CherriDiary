package com.cherri.diary.android.ui.design

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherri.diary.android.R

enum class CherriTab(val title: String, val icon: Int) {
    Overview("Tổng quan", R.drawable.ic_tab_dashboard),
    Live("Live", R.drawable.ic_tab_live),
    Orders("Đơn hàng", R.drawable.ic_tab_orders),
    Settings("Cài đặt", R.drawable.ic_tab_settings)
}

@Composable
fun BottomNavigationBar(selected: CherriTab, onSelect: (CherriTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        CherriTab.entries.forEach { tab ->
            NavigationBarItem(selected = tab == selected, onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text(tab.title, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = CherriColors.Subtitle,
                    unselectedTextColor = CherriColors.Subtitle))
        }
    }
}

enum class CommentStatus(val label: String, val background: Color, val foreground: Color) {
    New("Mới", Color(0xFFFCE4EC), Color(0xFF971B4B)),
    MissingPhone("Thiếu SĐT", Color(0xFFFFF0D8), Color(0xFF795500)),
    Confirmed("Đã chốt", Color(0xFFE2F2E8), Color(0xFF246544))
}

@Composable
fun CommentCard(name: String, handle: String, content: String, time: String,
    status: CommentStatus, onCheckout: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$handle · $time", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(content, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = status.background, shape = RoundedCornerShape(16.dp)) {
                    Text(status.label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = status.foreground, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onCheckout, enabled = status != CommentStatus.Confirmed,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (status == CommentStatus.Confirmed) "Đã chốt" else "Chốt đơn")
                }
            }
        }
    }
}

/** Render only during an actual loading/connection request, never for an idle empty feed. */
@Composable
fun CommentSkeleton(modifier: Modifier = Modifier) {
    val animation = rememberInfiniteTransition(label = "comment shimmer")
    val offset by animation.animateFloat(initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer offset")
    val brush = Brush.linearGradient(listOf(Color(0xFFF0E5E9), Color(0xFFFAF6F8), Color(0xFFF0E5E9)),
        start = Offset(offset, 0f), end = Offset(offset + 400f, 200f))
    Card(modifier.fillMaxWidth().semantics { contentDescription = "Đang tải bình luận" },
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(brush))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.6f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(brush))
                    Box(Modifier.fillMaxWidth(.4f).height(12.dp).clip(RoundedCornerShape(8.dp)).background(brush))
                }
            }
            Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Box(Modifier.fillMaxWidth(.7f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(brush))
        }
    }
}
