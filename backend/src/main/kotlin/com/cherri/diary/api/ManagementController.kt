package com.cherri.diary.api

import com.cherri.diary.domain.*
import com.cherri.diary.security.StaffPrincipal
import com.cherri.diary.service.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.MediaType
import java.util.Locale

data class EditUserRequest(@field:NotBlank @field:Size(max = 150) val fullName: String,
    val role: Role, val isActive: Boolean = true, @field:Size(max = 72) val password: String? = null)
data class BlacklistEntryRequest(val id: Long? = null, @field:Size(max = 150) val name: String? = null,
    @field:Size(max = 20) val phoneNumber: String? = null, @field:Size(max = 100) val tiktokId: String? = null,
    @field:NotBlank @field:Size(max = 5000) val notes: String)
data class ImageView(val imageUrl: String)

@RestController
@RequestMapping("/api/v1")
class ManagementController(private val users: UserRepository, private val products: ProductRepository,
    private val customers: CustomerRepository, private val passwords: PasswordEncoder, private val proofs: ProofStorageService) {
    @GetMapping("/users/me")
    fun me(@AuthenticationPrincipal staff: StaffPrincipal) = (users.findById(staff.id).orElse(null) ?: missing("Không tìm thấy tài khoản")).toView()

    @GetMapping("/management/products") @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    fun products(@RequestParam(defaultValue = "") q: String, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int): PageView<ProductView> {
        val result = products.findAll(search(q, listOf("shortCode", "name")), pagination(page, size))
        return PageView(result.content.map { it.toView().copy(costPrice = it.costPrice) }, page, size, result.totalElements, result.totalPages)
    }
    @DeleteMapping("/products/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    fun archiveProduct(@PathVariable id: Long): ProductView {
        val product = products.lockById(id) ?: missing("Không tìm thấy sản phẩm")
        product.status = ProductStatus.INACTIVE
        return product.toView().copy(costPrice = product.costPrice)
    }
    @PostMapping("/management/product-images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]) @PreAuthorize("hasRole('ADMIN')")
    fun image(@RequestPart("image") image: MultipartFile) = ImageView(proofs.store(image) ?: invalid("Chọn ảnh sản phẩm"))

    @GetMapping("/management/blacklist") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    fun blacklist(@RequestParam(defaultValue = "") q: String, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int): PageView<CustomerView> {
        val spec = search<Customer>(q, listOf("name", "phoneNumber", "tiktokId", "notes"))
            .and(Specification { root, _, cb -> cb.isTrue(root.get("isBlacklisted")) })
        val result = customers.findAll(spec, pagination(page, size))
        return PageView(result.content.map(Customer::toView), page, size, result.totalElements, result.totalPages)
    }
    @PostMapping("/management/blacklist") @PreAuthorize("hasRole('ADMIN')") @Transactional
    fun saveBlacklist(@Valid @RequestBody request: BlacklistEntryRequest): CustomerView {
        val phone = Identity.phone(request.phoneNumber)
        val nick = Identity.tiktok(request.tiktokId)
        if (phone == null && nick == null) invalid("Nhập SĐT hoặc TikTok ID")
        val candidates = listOfNotNull(phone?.let(customers::findByPhoneNumber), nick?.let(customers::findByTiktokId)).distinctBy { it.id }
        if (candidates.size > 1) conflict("SĐT và TikTok ID thuộc hai khách khác nhau")
        val id = request.id ?: candidates.singleOrNull()?.id
        if (request.id != null && candidates.any { it.id != request.id }) conflict("Thông tin liên hệ đã thuộc khách khác")
        val customer = id?.let { customers.lockById(it) ?: missing("Không tìm thấy khách hàng") }
            ?: Customer(name = request.name?.trim()?.takeIf { it.isNotEmpty() } ?: phone ?: nick!!)
        request.name?.trim()?.takeIf { it.isNotEmpty() }?.let { customer.name = it }
        if (request.id != null || phone != null) customer.phoneNumber = phone
        if (request.id != null || nick != null) customer.tiktokId = nick
        customer.notes = request.notes.trim(); customer.isBlacklisted = true
        return customers.saveAndFlush(customer).toView()
    }
    @GetMapping("/users") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    fun users(@RequestParam(defaultValue = "") q: String, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int): PageView<UserView> {
        val result = users.findAll(search(q, listOf("username", "fullName")), pagination(page, size))
        return PageView(result.content.map(User::toView), page, size, result.totalElements, result.totalPages)
    }
    @PutMapping("/users/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    fun updateUser(@PathVariable id: Long, @AuthenticationPrincipal actor: StaffPrincipal,
        @Valid @RequestBody request: EditUserRequest): UserView {
        users.findByRoleAndIsActiveOrderByIdAsc(Role.ROLE_ADMIN, true)
        val admin = users.lockById(actor.id) ?: missing("Không tìm thấy tài khoản")
        if (!admin.isActive || admin.role != Role.ROLE_ADMIN) conflict("Quyền Admin đã thay đổi, hãy đăng nhập lại")
        if (actor.id == id && (!request.isActive || request.role != Role.ROLE_ADMIN)) conflict("Không thể tự khóa hoặc hạ quyền tài khoản Admin đang dùng")
        val user = users.lockById(id) ?: missing("Không tìm thấy tài khoản")
        request.password?.takeIf { it.isNotEmpty() }?.let {
            if (it.length < 12 || it.toByteArray(Charsets.UTF_8).size > 72) invalid("Mật khẩu cần ít nhất 12 ký tự và tối đa 72 byte UTF-8")
            user.password = passwords.encode(it)
        }
        user.fullName = request.fullName.trim(); user.role = request.role; user.isActive = request.isActive
        return user.toView()
    }
    private fun pagination(page: Int, size: Int): PageRequest {
        if (page < 0 || size !in 1..100) invalid("page >= 0, size từ 1 đến 100")
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
    }
    private fun <T> search(query: String, fields: List<String>): Specification<T> = Specification { root, _, cb ->
        val q = query.trim().lowercase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        if (q.isEmpty()) cb.conjunction() else cb.or(*fields.map { cb.like(cb.lower(root.get(it)), "%$q%", '\\') }.toTypedArray())
    }
}
