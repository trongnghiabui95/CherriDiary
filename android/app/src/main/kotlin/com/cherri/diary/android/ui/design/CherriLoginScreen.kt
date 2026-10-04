package com.cherri.diary.android.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.cherri.diary.android.R

@Composable
fun CherriLoginScreen(initialServer: String, initialRemember: Boolean, busy: Boolean, error: String?,
    onLogin: (String, String, String, Boolean) -> Unit, onForgotPassword: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    // Keep passwords out of saved instance state.
    var password by remember { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(initialServer) }
    var rememberLogin by rememberSaveable { mutableStateOf(initialRemember) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    val submit = { if (!busy && username.isNotBlank() && password.isNotEmpty())
        onLogin(server.trim(), username.trim(), password, rememberLogin) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFE5EC), CherriColors.Cream, Color.White)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(16.dp))
            Image(painterResource(R.drawable.ic_cherri_custom), "Logo Cherri", Modifier.size(100.dp))
            Text("Cherri Diary", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("Một ngày bán hàng thật ngọt ngào", color = CherriColors.Subtitle)
            Card(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Chào bạn trở lại", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Đăng nhập để quản lý đơn và phiên live", color = CherriColors.Subtitle)
                    OutlinedTextField(username, { username = it }, label = { Text("Tên đăng nhập") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_account_outline), null, Modifier.size(22.dp)) },
                        enabled = !busy, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_lock_outline), null, Modifier.size(22.dp)) },
                        trailingIcon = { TextButton(onClick = { showPassword = !showPassword }, enabled = !busy) {
                            Text(if (showPassword) "Ẩn" else "Hiện") } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        enabled = !busy, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberLogin, { rememberLogin = it }, enabled = !busy)
                        Text("Ghi nhớ đăng nhập", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { submit() }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Đăng nhập", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onForgotPassword, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Quên mật khẩu?")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = { showServer = !showServer }, enabled = !busy) { Text("Cấu hình máy chủ") }
                    if (showServer) OutlinedTextField(server, { server = it }, label = { Text("Địa chỉ API") },
                        singleLine = true, enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            }
            Text("Live gọn gàng · Chốt đơn nhẹ nhàng", style = MaterialTheme.typography.bodySmall, color = CherriColors.Subtitle)
        }
    }
}
