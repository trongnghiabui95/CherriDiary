package com.cherri.diary.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class Role { ROLE_ADMIN, ROLE_STAFF }
enum class ProductStatus { ACTIVE, INACTIVE }
enum class OrderStatus { DRAFT, CONFIRMED, SHIPPING, COMPLETED, CANCELLED }
enum class PaymentStatus { UNPAID, PARTIALLY_PAID, PAID }
enum class PaymentMethod { COD, BANK_TRANSFER }
enum class Channel { TIKTOK, FACEBOOK, ZALO, PHONE }

@Entity @Table(name = "users")
class User(
    @Column(nullable = false, unique = true, length = 80) var username: String = "",
    @Column(nullable = false, length = 100) var password: String = "",
    @Column(nullable = false, length = 150) var fullName: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var role: Role = Role.ROLE_STAFF,
    @Column(nullable = false) var isActive: Boolean = true,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "categories")
class Category(
    @Column(nullable = false, unique = true, length = 150) var name: String = "",
    @Column(columnDefinition = "text") var description: String? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "products")
class Product(
    @Column(nullable = false, length = 200) var name: String = "",
    @Column(nullable = false, unique = true, length = 30) var shortCode: String = "",
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id") var category: Category? = null,
    @Column(columnDefinition = "text") var description: String? = null,
    @Column(length = 1000) var imageUrl: String? = null,
    @Column(nullable = false, precision = 19, scale = 2) var costPrice: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var sellingPrice: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var stockQuantity: Int = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) var status: ProductStatus = ProductStatus.ACTIVE,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "customers")
class Customer(
    @Column(nullable = false, length = 150) var name: String = "",
    @Column(unique = true, length = 20) var phoneNumber: String? = null,
    @Column(unique = true, length = 100) var tiktokId: String? = null,
    @Column(unique = true, length = 100) var facebookId: String? = null,
    @Column(length = 1000) var address: String? = null,
    @Column(nullable = false) var totalOrders: Int = 0,
    @Column(nullable = false) var isBlacklisted: Boolean = false,
    @Column(columnDefinition = "text") var notes: String? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "live_sessions")
class LiveSession(
    @Column(nullable = false, length = 200) var title: String = "",
    @Column(length = 100) var tiktokRoomId: String? = null,
    @Column(nullable = false) var startTime: Instant = Instant.now(),
    var endTime: Instant? = null,
    @Column(nullable = false, precision = 19, scale = 2) var totalRevenue: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false) var totalOrders: Int = 0,
    @Column(columnDefinition = "text") var notes: String? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "orders", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "request_id"])])
class Order(
    @Column(nullable = false, unique = true, length = 50) var orderCode: String = "CD-${UUID.randomUUID()}",
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) var customer: Customer = Customer(),
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) var user: User = User(),
    @ManyToOne(fetch = FetchType.LAZY) var liveSession: LiveSession? = null,
    @Column(nullable = false) var requestId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var channel: Channel = Channel.TIKTOK,
    @Column(nullable = false, precision = 19, scale = 2) var subtotalAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var depositAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var shippingFee: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var totalAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var paidAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var remainingAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var totalCost: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: OrderStatus = OrderStatus.CONFIRMED,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var paymentMethod: PaymentMethod = PaymentMethod.COD,
    @Column(length = 1000) var proofImageUrl: String? = null,
    @Column(length = 100) var trackingCode: String? = null,
    @Column(columnDefinition = "text") var commentRaw: String? = null,
    @Column(nullable = false) var stockReserved: Boolean = true,
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Entity @Table(name = "order_items")
class OrderItem(
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) var order: Order = Order(),
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) var product: Product = Product(),
    @Column(nullable = false) var quantity: Int = 1,
    @Column(nullable = false, precision = 19, scale = 2) var priceAtPurchase: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 19, scale = 2) var costAtPurchase: BigDecimal = BigDecimal.ZERO,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)
