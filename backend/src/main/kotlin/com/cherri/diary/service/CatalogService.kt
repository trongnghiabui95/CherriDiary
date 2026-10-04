package com.cherri.diary.service

import com.cherri.diary.api.*
import com.cherri.diary.domain.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale

@Service
class CatalogService(private val products: ProductRepository, private val categories: CategoryRepository,
    private val customers: CustomerRepository, private val users: UserRepository,
    private val sessions: LiveSessionRepository, private val passwords: PasswordEncoder) {
    @Transactional(readOnly = true)
    fun products(page: Int, size: Int, q: String = ""): PageView<ProductView> {
        validatePage(page, size)
        val term = q.trim().lowercase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val spec = org.springframework.data.jpa.domain.Specification<Product> { root, _, cb ->
            if (term.isEmpty()) cb.conjunction() else cb.or(cb.like(cb.lower(root.get("name")), "%$term%", '\\'), cb.like(cb.lower(root.get("shortCode")), "%$term%", '\\'))
        }
        val result = products.findAll(spec, PageRequest.of(page, size, Sort.by("shortCode")))
        return PageView(result.content.map(Product::toView), page, size, result.totalElements, result.totalPages)
    }
    @Transactional
    fun saveProduct(id: Long?, request: ProductRequest): ProductView {
        val product = id?.let { products.lockById(it) ?: missing("Không tìm thấy sản phẩm") } ?: Product()
        if (id != null && product.shortCode != Identity.code(request.shortCode)) conflict("Mã hàng không được đổi sau khi tạo")
        product.name = request.name.trim()
        product.shortCode = Identity.code(request.shortCode)
        product.category = request.categoryId?.let { categories.findById(it).orElse(null) ?: missing("Không tìm thấy danh mục") }
        product.description = request.description
        product.imageUrl = request.imageUrl
        product.costPrice = request.costPrice
        product.sellingPrice = request.sellingPrice
        product.stockQuantity = request.stockQuantity
        product.status = request.status
        return products.saveAndFlush(product).toView()
    }
    @Transactional(readOnly = true)
    fun categories() = categories.findAll(Sort.by("name")).map { CategoryView(it.id!!, it.name, it.description) }
    @Transactional
    fun category(request: CategoryRequest) = categories.saveAndFlush(Category(request.name.trim(), request.description)).let { CategoryView(it.id!!, it.name, it.description) }
    @Transactional
    fun createCustomer(request: CustomerRequest): CustomerView {
        val phone = Identity.phone(request.phoneNumber)
        val nick = Identity.tiktok(request.tiktokId)
        val facebook = request.facebookId?.trim()?.takeIf(String::isNotBlank)
        if (phone == null && nick == null && facebook == null) invalid("Cần thông tin liên hệ khách hàng")
        return customers.saveAndFlush(Customer(request.name.trim(), phone, nick, facebook, request.address, notes = request.notes)).toView()
    }
    @Transactional(readOnly = true)
    fun customers(page: Int, size: Int): PageView<CustomerView> {
        validatePage(page, size)
        val result = customers.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
        return PageView(result.content.map(Customer::toView), page, size, result.totalElements, result.totalPages)
    }
    @Transactional
    fun blacklist(id: Long, request: BlacklistRequest): CustomerView {
        val customer = customers.lockById(id) ?: missing("Không tìm thấy khách hàng")
        customer.isBlacklisted = request.isBlacklisted
        customer.notes = request.notes
        return customer.toView()
    }
    @Transactional
    fun user(request: UserRequest): UserView {
        if (request.password.toByteArray(Charsets.UTF_8).size > 72) invalid("Mật khẩu tối đa 72 byte UTF-8")
        return users.saveAndFlush(User(request.username.lowercase(Locale.ROOT), requireNotNull(passwords.encode(request.password)), request.fullName.trim(), request.role)).toView()
    }
    @Transactional(readOnly = true)
    fun liveSessions(page: Int, size: Int): PageView<LiveSessionView> {
        validatePage(page, size)
        val result = sessions.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")))
        return PageView(result.content.map(LiveSession::toView), page, size, result.totalElements, result.totalPages)
    }
    @Transactional
    fun createSession(request: LiveSessionRequest) = sessions.saveAndFlush(LiveSession(request.title.trim(), request.tiktokRoomId, notes = request.notes)).toView()
    @Transactional
    fun endSession(id: Long): LiveSessionView {
        val session = sessions.lockById(id) ?: missing("Không tìm thấy phiên live")
        if (session.endTime == null) session.endTime = Instant.now()
        return session.toView()
    }
    private fun validatePage(page: Int, size: Int) {
        if (page < 0 || size !in 1..100) invalid("page >= 0, size từ 1 đến 100")
    }
}
