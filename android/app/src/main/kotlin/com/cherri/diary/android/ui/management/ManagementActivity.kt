package com.cherri.diary.android.ui.management

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherri.diary.android.cherri
import com.cherri.diary.android.R
import com.cherri.diary.android.data.*
import com.cherri.diary.android.live.FloatingWindowService
import com.cherri.diary.android.ui.LoginActivity
import kotlinx.coroutines.delay
import java.net.URI

@Composable fun ManagementTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFB22D54), secondary = Color(0xFF795566),
        background = Color(0xFFFFF7F9), surface = Color.White), content = content)
}
@Composable fun ManagementMenu(open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Quản lý cửa hàng", style = MaterialTheme.typography.titleLarge)
        Text("Sản phẩm, khách hàng và đội ngũ", style = MaterialTheme.typography.bodyMedium)
        listOf(Triple("products", "Sản phẩm", "Giá bán · Tồn kho · Ảnh sản phẩm"),
            Triple("blacklist", "Danh sách đen", "Khách boom hàng · Gỡ chặn (Admin)"),
            Triple("account", "Tài khoản", "Thông tin · Nhân viên · Đăng xuất")).forEach { (route, title, detail) ->
            ElevatedCard(onClick = { open(route) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(if (route == "products") R.drawable.ic_tab_orders else if (route == "blacklist") R.drawable.ic_tab_live else R.drawable.ic_tab_settings), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall) }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
class ManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ManagementTheme { ManagementNavigation(intent.getStringExtra("route") ?: "account", { finish() }, logout = {
            stopService(Intent(this, FloatingWindowService::class.java)); cherri.tokens.clear()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }) } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ManagementNavigation(start: String, close: () -> Unit, logout: () -> Unit, vm: ManagementViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: start
    val state by vm.state.collectAsStateWithLifecycle()
    val titles = mapOf("products" to "Sản phẩm", "blacklist" to "Danh sách đen", "account" to "Tài khoản", "users" to "Nhân viên")
    Scaffold(topBar = { TopAppBar(title = { Text(titles[route] ?: "Cài đặt") }, navigationIcon = {
        TextButton(onClick = { if (!nav.popBackStack()) close() }) { Text("‹ Quay lại") }
    }) }) { padding ->
        NavHost(nav, startDestination = if (start in titles) start else "account", modifier = Modifier.padding(padding).imePadding()) {
            composable("account") {
                LaunchedEffect(Unit) { vm.load("account") }
                var confirm by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Feedback(state) { vm.load("account") }
                    state.me?.let { user ->
                        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(user.fullName, style = MaterialTheme.typography.headlineSmall); Text("@${user.username}")
                            Text(if (state.admin) "Quản trị viên" else "Nhân viên")
                        } }
                        if (state.admin) Button(onClick = { nav.navigate("users") }, modifier = Modifier.fillMaxWidth()) { Text("Quản lý nhân viên") }
                    }
                    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
                }
                if (confirm) Confirm("Đăng xuất khỏi Cherri Diary?", { confirm = false }) { confirm = false; logout() }
            }
            listOf("products", "blacklist", "users").forEach { target -> composable(target) { ManagementList(target, state, vm) } }
        }
    }
}
@Composable private fun Feedback(state: ManagementState, retry: () -> Unit) {
    if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Thử lại") } }
}
@Composable private fun ManagementList(route: String, state: ManagementState, vm: ManagementViewModel) {
    var q by rememberSaveable(route) { mutableStateOf("") }
    var editing by remember(route) { mutableStateOf<Any?>(null) }
    var showEditor by remember(route) { mutableStateOf(false) }
    var deleting by remember(route) { mutableStateOf<Any?>(null) }
    LaunchedEffect(route, q) { delay(300); vm.load(route, q) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Tìm kiếm…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Feedback(state) { vm.load(route, q, state.page) }
        if (state.admin) Button(onClick = { editing = null; vm.clearError(); showEditor = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(when (route) { "products" -> "+ Thêm sản phẩm"; "blacklist" -> "+ Thêm vào danh sách đen"; else -> "+ Thêm nhân viên" })
        }
        if (state.me != null && !state.admin && route != "products") { Text("Mục này dành cho Admin."); return@Column }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            when (route) {
                "products" -> items(state.products, key = { it.id }) { p ->
                    ItemCard("${p.shortCode} · ${p.name}", "${p.sellingPrice.toPlainString()} đ · Tồn ${p.stockQuantity}\n${if (p.status == "ACTIVE") "Đang bán" else "Ngừng bán"}", state.admin, state.busy,
                        { editing = p; vm.clearError(); showEditor = true }, { deleting = p }) { p.imageUrl?.let { ProductImage(it, vm) } }
                }
                "blacklist" -> items(state.blacklist, key = { it.id }) { c -> ItemCard(c.name, listOfNotNull(c.phoneNumber, c.tiktokId?.let { "@$it" }, c.notes).joinToString("\n"), true, state.busy,
                    { editing = c; vm.clearError(); showEditor = true }, { deleting = c }) }
                "users" -> items(state.users, key = { it.id }) { u -> ItemCard(u.fullName, "@${u.username} · ${if (u.role == "ROLE_ADMIN") "Admin" else "Nhân viên"}\n${if (u.isActive) "Hoạt động" else "Đã khóa"}", true, state.busy,
                    { editing = u; vm.clearError(); showEditor = true }, if (u.id != state.me?.id && u.isActive) ({ deleting = u }) else null) }
            }
            if (!state.loading && state.total == 0L && state.error == null) item { Text("Chưa có dữ liệu phù hợp. Hãy thêm mới hoặc đổi từ khóa.", modifier = Modifier.padding(16.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.load(route, q, state.page - 1) }, enabled = state.page > 0 && !state.loading && !state.busy) { Text("‹ Trước") }
            Text("${state.total} mục · ${if (state.pages == 0) 0 else state.page + 1}/${state.pages}", modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { vm.load(route, q, state.page + 1) }, enabled = state.page + 1 < state.pages && !state.loading && !state.busy) { Text("Sau ›") }
        }
    }
    if (showEditor) Editor(route, editing, state, vm) { showEditor = false }
    deleting?.let { target -> Confirm(when (target) { is ProductView -> "Ngừng bán ${target.name}? Lịch sử đơn được giữ nguyên."; is CustomerView -> "Gỡ ${target.name} khỏi danh sách đen?"; else -> "Khóa tài khoản nhân viên này?" }, { deleting = null }, state.busy) {
        vm.action({ deleting = null }) { when (target) {
            is ProductView -> archiveProduct(target.id)
            is CustomerView -> unblacklist(target.id, BlacklistRequest(false, target.notes))
            is UserView -> editUser(target.id, EditUserRequest(target.fullName, target.role, false, null))
        } }
    } }
}
@Composable private fun ItemCard(title: String, detail: String, admin: Boolean, busy: Boolean, edit: () -> Unit, remove: (() -> Unit)?, extra: @Composable () -> Unit = {}) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extra(); Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodyMedium)
        if (admin) Row { TextButton(onClick = edit, enabled = !busy) { Text("Sửa") }; remove?.let { TextButton(onClick = it, enabled = !busy) { Text("Gỡ / Ngừng dùng", color = MaterialTheme.colorScheme.error) } } }
    } }
}
@Composable private fun Confirm(message: String, dismiss: () -> Unit, busy: Boolean = false, action: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Xác nhận") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = action, enabled = !busy) { Text("Xác nhận") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Hủy") } })
}
@Composable private fun ProductImage(url: String, vm: ManagementViewModel) {
    val base = URI(vm.app.tokens.baseUrl())
    val resolved = runCatching { base.resolve(url) }.getOrNull() ?: return
    val request = ImageRequest.Builder(LocalContext.current).data(resolved.toString())
    if (resolved.scheme == base.scheme && resolved.host == base.host && resolved.port == base.port && resolved.path.startsWith("/api/v1/proofs/")) {
        vm.app.tokens.token()?.let { request.addHeader("Authorization", "Bearer $it") }
    }
    AsyncImage(request.build(), contentDescription = "Ảnh sản phẩm", modifier = Modifier.fillMaxWidth().height(140.dp))
}

@Composable private fun Editor(route: String, target: Any?, state: ManagementState, vm: ManagementViewModel, dismiss: () -> Unit) {
    val product = target as? ProductView
    val customer = target as? CustomerView
    val user = target as? UserView
    var name by remember { mutableStateOf(product?.name ?: customer?.name ?: user?.fullName ?: "") }
    var code by remember { mutableStateOf(product?.shortCode ?: user?.username ?: "") }
    var price by remember { mutableStateOf(product?.sellingPrice?.toPlainString() ?: "") }
    var cost by remember { mutableStateOf(product?.costPrice?.toPlainString() ?: "") }
    var stock by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "0") }
    var image by remember { mutableStateOf(product?.imageUrl ?: "") }
    var active by remember { mutableStateOf(product?.status != "INACTIVE" && user?.isActive != false) }
    var phone by remember { mutableStateOf(customer?.phoneNumber ?: "") }
    var nick by remember { mutableStateOf(customer?.tiktokId ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(user?.role ?: "ROLE_STAFF") }
    var localError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.upload(it) { image = it } } }
    AlertDialog(onDismissRequest = { if (!state.busy) dismiss() }, title = { Text(if (target == null) "Thêm mới" else "Chỉnh sửa") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("${if (route == "products") "Tên sản phẩm" else if (route == "users") "Họ tên" else "Tên khách hàng"}") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
            when (route) {
                "products" -> {
                    item { Input(code, { code = it }, "Mã ngắn", enabled = product == null && !state.busy) }
                    if (product != null) item { Text("Mã ngắn được giữ cố định để bảo toàn lịch sử đơn.", style = MaterialTheme.typography.bodySmall) }
                    item { Input(price, { price = it }, "Giá bán (đ)", KeyboardType.Decimal, !state.busy) }
                    item { Input(cost, { cost = it }, "Giá vốn (đ)", KeyboardType.Decimal, !state.busy) }
                    item { Input(stock, { stock = it }, "Tồn kho", KeyboardType.Number, !state.busy) }
                    item { Input(image, { image = it }, "URL ảnh (tùy chọn)", enabled = !state.busy) }
                    item { OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !state.busy) { Text("Chọn ảnh từ máy") } }
                    if (image.isNotBlank()) item { ProductImage(image, vm) }
                    item { Row { Checkbox(active, { active = it }, enabled = !state.busy); Text("Đang bán", Modifier.padding(top = 12.dp)) } }
                }
                "blacklist" -> {
                    item { Input(phone, { phone = it }, "Số điện thoại", KeyboardType.Phone, !state.busy) }
                    item { Input(nick, { nick = it }, "TikTok ID (ví dụ @khachhang)", enabled = !state.busy) }
                    item { OutlinedTextField(notes, { notes = it }, label = { Text("Lý do boom hàng") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
                }
                "users" -> {
                    item { Input(code, { code = it }, "Tên đăng nhập", enabled = user == null && !state.busy) }
                    item { OutlinedTextField(password, { password = it }, label = { Text(if (user == null) "Mật khẩu" else "Mật khẩu mới (để trống để giữ)") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
                    item { Text("Mật khẩu tối thiểu 12 ký tự, tối đa 72 byte.", style = MaterialTheme.typography.bodySmall) }
                    item { Row { Checkbox(role == "ROLE_ADMIN", { role = if (it) "ROLE_ADMIN" else "ROLE_STAFF" }, enabled = !state.busy && user?.id != state.me?.id); Text("Quyền Admin", Modifier.padding(top = 12.dp)) } }
                    if (user != null) item { Row { Checkbox(active, { active = it }, enabled = !state.busy && user.id != state.me?.id); Text("Tài khoản hoạt động", Modifier.padding(top = 12.dp)) } }
                }
            }
            item { (localError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } },
        confirmButton = { TextButton(enabled = !state.busy, onClick = {
            localError = null
            val validation = runCatching {
                when (route) {
                    "products" -> {
                        require(name.isNotBlank() && code.isNotBlank()) { "Nhập tên và mã sản phẩm" }
                        val selling = price.toBigDecimalOrNull(); val buying = cost.toBigDecimalOrNull(); val quantity = stock.toIntOrNull()
                        require(selling != null && buying != null && selling.signum() >= 0 && buying.signum() >= 0 && quantity != null && quantity >= 0) { "Giá và tồn kho cần là số không âm" }
                        val request = ProductRequest(name.trim(), code.trim(), buying, selling, quantity, image.trim().ifBlank { null }, if (active) "ACTIVE" else "INACTIVE", product?.description, product?.categoryId)
                        vm.action(dismiss) { if (product == null) createProduct(request) else editProduct(product.id, request) }
                    }
                    "blacklist" -> {
                        require(phone.isNotBlank() || nick.isNotBlank()) { "Nhập SĐT hoặc TikTok ID" }; require(notes.isNotBlank()) { "Nhập lý do boom hàng" }
                        val request = BlacklistEntryRequest(name.trim().ifBlank { null }, phone.trim().ifBlank { null }, nick.trim().ifBlank { null }, notes.trim(), customer?.id)
                        vm.action(dismiss) { saveBlacklist(request) }
                    }
                    "users" -> {
                        require(name.isNotBlank() && code.isNotBlank()) { "Nhập họ tên và tên đăng nhập" }
                        if (user == null || password.isNotEmpty()) require(password.length >= 12 && password.toByteArray().size <= 72) { "Mật khẩu tối thiểu 12 ký tự, tối đa 72 byte" }
                        vm.action(dismiss) { if (user == null) createUser(UserRequest(code.trim(), name.trim(), password, role)) else editUser(user.id, EditUserRequest(name.trim(), role, active, password.ifEmpty { null })) }
                    }
                }
            }
            validation.exceptionOrNull()?.let { localError = it.message }
        }) { Text("Lưu") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !state.busy) { Text("Hủy") } })
}
@Composable private fun Input(value: String, change: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
}
