package com.cherri.diary.android.ui.management

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cherri.diary.android.CherriApplication
import com.cherri.diary.android.data.*
import com.cherri.diary.android.ui.errorText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

 data class ManagementState(val me: UserView? = null, val loading: Boolean = false, val busy: Boolean = false,
    val error: String? = null, val products: List<ProductView> = emptyList(), val blacklist: List<CustomerView> = emptyList(),
    val users: List<UserView> = emptyList(), val page: Int = 0, val pages: Int = 0, val total: Long = 0) {
    val admin get() = me?.role == "ROLE_ADMIN"
}
class ManagementViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as CherriApplication
    private val mutable = MutableStateFlow(ManagementState())
    val state = mutable.asStateFlow()
    private var loadingJob: Job? = null
    private var route = "account"
    private var query = ""
    fun load(target: String, q: String = "", page: Int = 0) {
        route = target; query = q
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val api = app.api.service()
                val me = api.me()
                mutable.value = mutable.value.copy(me = me)
                when (target) {
                    "products" -> { val data = if (me.role == "ROLE_ADMIN") api.manageProducts(q, page) else api.products(page, 20, q)
                        mutable.value = mutable.value.copy(products = data.content, page = data.page, pages = data.totalPages, total = data.totalElements) }
                    "blacklist" -> { require(me.role == "ROLE_ADMIN") { "Chỉ Admin được quản lý danh sách đen" }
                        val data = api.blacklist(q, page); mutable.value = mutable.value.copy(blacklist = data.content, page = data.page, pages = data.totalPages, total = data.totalElements) }
                    "users" -> { require(me.role == "ROLE_ADMIN") { "Chỉ Admin được quản lý nhân viên" }
                        val data = api.users(q, page); mutable.value = mutable.value.copy(users = data.content, page = data.page, pages = data.totalPages, total = data.totalElements) }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.value = mutable.value.copy(error = errorText(e)) }
            finally { if (isActive) mutable.value = mutable.value.copy(loading = false) }
        }
    }
    fun action(done: () -> Unit = {}, block: suspend ApiService.() -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { app.api.service().block(); done(); load(route, query, mutable.value.page) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.value = mutable.value.copy(error = errorText(e)) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun upload(uri: Uri, done: (String) -> Unit) {
        action(block = {
            val bytes = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 5 * 1024 * 1024) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Không đọc được ảnh")
            }
            require(bytes.size <= 5 * 1024 * 1024) { "Ảnh tối đa 5 MB" }
            val part = MultipartBody.Part.createFormData("image", "product.png", bytes.toRequestBody("application/octet-stream".toMediaType()))
            done(uploadProductImage(part).imageUrl)
        })
    }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
}
