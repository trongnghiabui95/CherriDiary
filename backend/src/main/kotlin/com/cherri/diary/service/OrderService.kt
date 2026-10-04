package com.cherri.diary.service

import com.cherri.diary.api.*
import com.cherri.diary.domain.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@Service
class OrderService(private val users: UserRepository, private val products: ProductRepository,
    private val customers: CustomerRepository, private val orders: OrderRepository,
    private val sessions: LiveSessionRepository, private val proofs: ProofStorageService,
    private val entityManager: EntityManager) {

    @Transactional
    fun fastCreate(userId: Long, request: FastCreateRequest, proof: MultipartFile?): OrderView {
        // Same staff's retries serialize, so a duplicate request can never reserve stock twice.
        val user = users.lockById(userId) ?: missing("Không tìm thấy nhân viên")
        if (!user.isActive) conflict("Tài khoản đã bị khóa")
        orders.findByUserIdAndRequestId(userId, request.requestId)?.let { return it.toView() }
        if (request.status !in setOf(OrderStatus.DRAFT, OrderStatus.CONFIRMED)) invalid("Đơn mới chỉ có trạng thái DRAFT hoặc CONFIRMED")
        if (request.items.size > 50) invalid("Đơn tối đa 50 dòng hàng")
        val customer = resolveCustomer(request)
        if (customer.isBlacklisted && !request.acknowledgeBlacklist) conflict("Khách nằm trong danh sách đen. Cần xác nhận cảnh báo trước khi chốt.")
        val order = Order(customer = customer, user = user, requestId = request.requestId,
            channel = request.channel, status = if (request.items.isEmpty()) OrderStatus.DRAFT else request.status, stockReserved = request.items.isNotEmpty(), commentRaw = request.commentRaw,
            depositAmount = money(request.depositAmount), shippingFee = money(request.shippingFee), paymentMethod = request.paymentMethod)
        addProducts(order, request.items)
        recalculate(order)
        request.liveSessionId?.let { id ->
            val session = sessions.lockById(id) ?: missing("Không tìm thấy phiên live")
            if (session.endTime != null) conflict("Phiên live đã kết thúc")
            order.liveSession = session
            session.totalOrders += 1
            session.totalRevenue += order.subtotalAmount
        }
        customer.totalOrders += 1
        order.proofImageUrl = proofs.store(proof)
        return orders.saveAndFlush(order).toView()
    }

    private fun addProducts(order: Order, items: List<QuickOrderItem>) {
        val quantities = items.groupBy { Identity.code(it.shortCode) }.mapValues { (_, items) ->
            if (items.any { it.quantity !in 1..10000 }) invalid("Số lượng không hợp lệ")
            val total = items.sumOf { it.quantity.toLong() }
            if (total > 10000) invalid("Một mã hàng tối đa 10000 sản phẩm")
            total.toInt()
        }
        // Fixed order avoids deadlocks for baskets containing overlapping products.
        val lockedProducts = quantities.keys.sorted().associateWith { code ->
            var product = products.lockByShortCode(code)
            if (product == null) {
                val prices = items.filter { Identity.code(it.shortCode) == code }.mapNotNull { it.newProductPrice }.map(::money).distinct()
                if (prices.size != 1) invalid("Nhập một giá bán cho mã hàng mới $code")
                // The unique code and ON CONFLICT serialize concurrent creation without duplicate products.
                entityManager.createNativeQuery("""INSERT INTO products (name, short_code, cost_price, selling_price, stock_quantity, status, description)
                    VALUES (:code, :code, 0, :price, :quantity, 'ACTIVE', 'Tạo nhanh khi chốt đơn livestream; cần bổ sung giá vốn và tồn kho')
                    ON CONFLICT (short_code) DO NOTHING""")
                    .setParameter("code", code).setParameter("price", prices.single()).setParameter("quantity", quantities.getValue(code)).executeUpdate()
                product = products.lockByShortCode(code) ?: missing("Không tìm thấy mã hàng $code")
            }
            product.also {
                entityManager.refresh(it, LockModeType.PESSIMISTIC_WRITE)
            }
        }
        for ((code, product) in lockedProducts) {
            val quantity = quantities.getValue(code)
            if (product.status != ProductStatus.ACTIVE) conflict("Mã hàng $code ngừng bán")
            if (product.stockQuantity < quantity) conflict("$code không đủ kho (còn ${product.stockQuantity})")
            product.stockQuantity -= quantity
            order.items += OrderItem(order, product, quantity, product.sellingPrice, product.costPrice)
            order.subtotalAmount += product.sellingPrice * quantity.toBigDecimal()
            order.totalCost += product.costPrice * quantity.toBigDecimal()
        }
    }

    @Transactional
    fun completeDraft(id: Long, items: List<QuickOrderItem>): OrderView {
        val order = orders.lockById(id) ?: missing("Không tìm thấy đơn hàng")
        if (order.status != OrderStatus.DRAFT) conflict("Chỉ bổ sung mã hàng cho đơn nháp")
        if (items.isEmpty() || items.size > 50) invalid("Nhập từ 1 đến 50 dòng hàng")
        if (order.items.isNotEmpty()) {
            val desired = items.groupBy { Identity.code(it.shortCode) }.mapValues { it.value.sumOf { line -> line.quantity.toLong() } }
            val current = order.items.associate { it.product.shortCode to it.quantity.toLong() }
            if (desired == current) return order.toView()
            conflict("Đơn đã có sản phẩm; không thể ghi đè danh sách")
        }
        if (order.customer.isBlacklisted) conflict("Khách đang trong danh sách đen; cần xử lý cảnh báo trước khi bổ sung hàng")
        addProducts(order, items)
        order.stockReserved = true
        recalculate(order)
        order.liveSession?.id?.let { sessionId ->
            val session = sessions.lockById(sessionId) ?: missing("Không tìm thấy phiên live")
            session.totalRevenue += order.subtotalAmount
        }
        return orders.saveAndFlush(order).toView()
    }
    private fun resolveCustomer(request: FastCreateRequest): Customer {
        val phone = Identity.phone(request.phoneNumber)
        val nick = Identity.tiktok(request.tiktokId)
        val facebook = request.facebookId?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = listOfNotNull(
            phone?.let(customers::findByPhoneNumber), nick?.let(customers::findByTiktokId),
            facebook?.let(customers::findByFacebookId), request.customerId?.let { customers.findById(it).orElseThrow { ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng") } }
        ).distinctBy { it.id }
        if (candidates.size > 1) conflict("Các thông tin liên hệ thuộc những khách hàng khác nhau")
        val customer = candidates.singleOrNull()?.let {
            customers.lockById(it.id!!)!!.also { locked -> entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE) }
        } ?: run {
            if (phone == null && nick == null && facebook == null && (request.items.isNotEmpty() || (request.customerName.isNullOrBlank() && request.commentRaw.isNullOrBlank()))) invalid("Cần thông tin khách hoặc comment để lưu nháp")
            customers.saveAndFlush(Customer(name = request.customerName?.trim()?.takeIf { it.isNotEmpty() } ?: phone ?: nick ?: facebook ?: "Khách từ comment",
                phoneNumber = phone, tiktokId = nick, facebookId = facebook, address = request.address))
        }
        if ((phone != null && customer.phoneNumber != null && phone != customer.phoneNumber) ||
            (nick != null && customer.tiktokId != null && nick != customer.tiktokId) ||
            (facebook != null && customer.facebookId != null && facebook != customer.facebookId)) conflict("Thông tin liên hệ không khớp hồ sơ khách hàng")
        if (customer.phoneNumber == null) customer.phoneNumber = phone
        if (customer.tiktokId == null) customer.tiktokId = nick
        if (customer.facebookId == null) customer.facebookId = facebook
        request.customerName?.trim()?.takeIf { it.isNotEmpty() }?.let { customer.name = it }
        request.address?.trim()?.takeIf { it.isNotEmpty() }?.let { customer.address = it }
        return customer
    }

    @Transactional
    fun updateStatus(id: Long, target: OrderStatus): OrderView {
        val order = orders.lockById(id) ?: missing("Không tìm thấy đơn hàng")
        if (order.status == target) return order.toView()
        val allowed = when (order.status) {
            OrderStatus.DRAFT -> setOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
            OrderStatus.CONFIRMED -> setOf(OrderStatus.SHIPPING, OrderStatus.CANCELLED)
            OrderStatus.SHIPPING -> setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)
            OrderStatus.COMPLETED, OrderStatus.CANCELLED -> emptySet()
        }
        if (target !in allowed) conflict("Không thể chuyển ${order.status} sang $target")
        if (target == OrderStatus.CONFIRMED && order.items.isEmpty()) conflict("Bổ sung mã hàng trước khi xác nhận đơn nháp")
        if (target == OrderStatus.COMPLETED && order.remainingAmount.signum() != 0) conflict("Cần ghi nhận đủ thanh toán trước khi hoàn tất đơn")
        if (target == OrderStatus.CANCELLED) {
            val customer = customers.lockById(order.customer.id!!)!!
            entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE)
            order.items.sortedBy { it.product.shortCode }.forEach { item ->
                val product = products.lockById(item.product.id!!)!!
                entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE)
                product.stockQuantity = Math.addExact(product.stockQuantity, item.quantity)
            }
            customer.totalOrders -= 1
            order.liveSession?.id?.let { sessionId ->
                val session = sessions.lockById(sessionId)!!
                session.totalOrders -= 1
                session.totalRevenue -= order.subtotalAmount
            }
            order.stockReserved = false
        }
        order.status = target
        return order.toView()
    }

    @Transactional
    fun updatePayment(id: Long, request: PaymentRequest): OrderView {
        val order = orders.lockById(id) ?: missing("Không tìm thấy đơn hàng")
        if (order.status == OrderStatus.CANCELLED) conflict("Đơn đã hủy; hoàn tiền cần xử lý riêng")
        order.depositAmount = money(request.depositAmount)
        order.paidAmount = money(request.paidAmount)
        order.paymentMethod = request.paymentMethod
        recalculate(order)
        if (order.status == OrderStatus.COMPLETED && order.remainingAmount.signum() != 0) conflict("Đơn hoàn tất phải được thanh toán đủ")
        return order.toView()
    }

    @Transactional
    fun updateTracking(id: Long, request: TrackingRequest): OrderView {
        val order = orders.lockById(id) ?: missing("Không tìm thấy đơn hàng")
        if (order.status in setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)) conflict("Không thể sửa vận đơn cho đơn đã kết thúc")
        order.trackingCode = request.trackingCode.trim().takeIf { it.isNotEmpty() } ?: invalid("Mã vận đơn không được rỗng")
        return order.toView()
    }

    @Transactional(readOnly = true)
    fun get(id: Long) = orders.findById(id).orElseThrow { ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng") }.toView()

    @Transactional(readOnly = true)
    fun list(phone: String?, tiktokId: String?, orderCode: String?, status: OrderStatus?, channel: Channel?, page: Int, size: Int): PageView<OrderView> {
        if (page < 0 || size !in 1..100) invalid("page >= 0, size từ 1 đến 100")
        val normalizedPhone = Identity.phone(phone)
        val normalizedNick = Identity.tiktok(tiktokId)
        val spec = Specification<Order> { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            normalizedPhone?.let { predicates += cb.equal(root.get<Customer>("customer").get<String>("phoneNumber"), it) }
            normalizedNick?.let { predicates += cb.equal(root.get<Customer>("customer").get<String>("tiktokId"), it) }
            orderCode?.takeIf { it.isNotBlank() }?.let { predicates += cb.equal(root.get<String>("orderCode"), it) }
            status?.let { predicates += cb.equal(root.get<OrderStatus>("status"), it) }
            channel?.let { predicates += cb.equal(root.get<Channel>("channel"), it) }
            cb.and(*predicates.toTypedArray())
        }
        val result = orders.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
        return PageView(result.content.map(Order::toView), page, size, result.totalElements, result.totalPages)
    }

    private fun money(value: BigDecimal): BigDecimal {
        if (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 17) invalid("Số tiền không hợp lệ")
        return value.setScale(2)
    }

    private fun recalculate(order: Order) {
        order.totalAmount = order.subtotalAmount - order.depositAmount + order.shippingFee
        order.remainingAmount = order.totalAmount - order.paidAmount
        if (order.totalAmount.signum() < 0 || order.remainingAmount.signum() < 0) invalid("Tổng tiền đã thu không được vượt tiền hàng cộng phí ship")
        order.paymentStatus = when {
            order.items.isEmpty() -> PaymentStatus.UNPAID
            order.remainingAmount.signum() == 0 -> PaymentStatus.PAID
            (order.depositAmount + order.paidAmount).signum() > 0 -> PaymentStatus.PARTIALLY_PAID
            else -> PaymentStatus.UNPAID
        }
    }
}
