package com.cherri.diary.api

import com.cherri.diary.domain.*
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class LoginRequest(@field:NotBlank val username: String, @field:NotBlank val password: String)
data class UserView(val id: Long, val username: String, val fullName: String, val role: Role, val isActive: Boolean = true)
data class LoginResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long, val user: UserView)
fun User.toView() = UserView(id!!, username, fullName, role, isActive)

data class UserRequest(
    @field:Pattern(regexp = "[a-zA-Z0-9_.-]{3,80}") val username: String,
    @field:Size(min = 12, max = 72) val password: String,
    @field:NotBlank @field:Size(max = 150) val fullName: String,
    val role: Role = Role.ROLE_STAFF,
)
data class CategoryRequest(@field:NotBlank @field:Size(max = 150) val name: String, @field:Size(max = 5000) val description: String? = null)
data class CategoryView(val id: Long, val name: String, val description: String?)
data class ProductRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,29}") val shortCode: String,
    @field:Positive val categoryId: Long? = null,
    @field:Size(max = 5000) val description: String? = null,
    @field:Size(max = 1000) val imageUrl: String? = null,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val costPrice: BigDecimal,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val sellingPrice: BigDecimal,
    @field:Min(0) val stockQuantity: Int,
    val status: ProductStatus = ProductStatus.ACTIVE,
)
data class ProductView(val id: Long, val categoryId: Long?, val name: String, val shortCode: String,
    val sellingPrice: BigDecimal, val stockQuantity: Int, val status: ProductStatus,
    val description: String?, val imageUrl: String?, val costPrice: BigDecimal? = null)
fun Product.toView() = ProductView(id!!, category?.id, name, shortCode, sellingPrice, stockQuantity, status, description, imageUrl)

data class CustomerRequest(
    @field:NotBlank @field:Size(max = 150) val name: String,
    @field:Size(max = 20) val phoneNumber: String? = null,
    @field:Size(max = 100) val tiktokId: String? = null,
    @field:Size(max = 100) val facebookId: String? = null,
    @field:Size(max = 1000) val address: String? = null,
    @field:Size(max = 5000) val notes: String? = null,
)
data class BlacklistRequest(val isBlacklisted: Boolean, @field:Size(max = 5000) val notes: String? = null)
data class CustomerView(val id: Long, val name: String, val phoneNumber: String?, val tiktokId: String?,
    val facebookId: String?, val address: String?, val totalOrders: Int, val isBlacklisted: Boolean, val notes: String?)
fun Customer.toView() = CustomerView(id!!, name, phoneNumber, tiktokId, facebookId, address, totalOrders, isBlacklisted, notes)

data class ParseCommentRequest(@field:NotBlank @field:Size(max = 4000) val comment: String)
data class ParsedItem(val shortCode: String, val quantity: Int, val product: ProductView?)
data class ParsedComment(val commentRaw: String, val phoneNumber: String?, val tiktokId: String?,
    val customer: CustomerView?, val items: List<ParsedItem>, val isBlacklisted: Boolean, val warnings: List<String>)
data class QuickOrderItem(
    @field:Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,29}") val shortCode: String,
    @field:Min(1) @field:Max(10000) val quantity: Int = 1,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val newProductPrice: BigDecimal? = null,
)
data class FastCreateRequest(
    val requestId: UUID,
    @field:Valid @field:Size(max = 50) val items: List<QuickOrderItem>,
    @field:Positive val customerId: Long? = null,
    @field:Size(max = 150) val customerName: String? = null,
    @field:Size(max = 20) val phoneNumber: String? = null,
    @field:Size(max = 100) val tiktokId: String? = null,
    @field:Size(max = 100) val facebookId: String? = null,
    @field:Size(max = 1000) val address: String? = null,
    @field:Positive val liveSessionId: Long? = null,
    val channel: Channel = Channel.TIKTOK,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val depositAmount: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val shippingFee: BigDecimal = BigDecimal.ZERO,
    val paymentMethod: PaymentMethod = PaymentMethod.COD,
    @field:Size(max = 4000) val commentRaw: String? = null,
    val acknowledgeBlacklist: Boolean = false,
    val status: OrderStatus = OrderStatus.CONFIRMED,
)
data class StatusRequest(val status: OrderStatus)
data class DraftItemsRequest(@field:Valid @field:Size(min = 1, max = 50) val items: List<QuickOrderItem>)
data class PaymentRequest(
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val depositAmount: BigDecimal,
    @field:DecimalMin("0") @field:Digits(integer = 17, fraction = 2) val paidAmount: BigDecimal,
    val paymentMethod: PaymentMethod,
)
data class TrackingRequest(@field:NotBlank @field:Size(max = 100) val trackingCode: String)
data class OrderItemView(val productId: Long, val shortCode: String, val quantity: Int,
    val priceAtPurchase: BigDecimal, val costAtPurchase: BigDecimal)
data class OrderView(val id: Long, val orderCode: String, val customer: CustomerView, val userId: Long,
    val liveSessionId: Long?, val requestId: UUID, val channel: Channel, val subtotalAmount: BigDecimal,
    val depositAmount: BigDecimal, val shippingFee: BigDecimal, val totalAmount: BigDecimal, val paidAmount: BigDecimal,
    val remainingAmount: BigDecimal, val totalCost: BigDecimal, val status: OrderStatus, val paymentStatus: PaymentStatus,
    val paymentMethod: PaymentMethod, val proofImageUrl: String?, val trackingCode: String?,
    val commentRaw: String?, val createdAt: Instant, val items: List<OrderItemView>)
fun Order.toView() = OrderView(id!!, orderCode, customer.toView(), user.id!!, liveSession?.id, requestId, channel,
    subtotalAmount, depositAmount, shippingFee, totalAmount, paidAmount, remainingAmount, totalCost, status,
    paymentStatus, paymentMethod, proofImageUrl, trackingCode, commentRaw, createdAt,
    items.map { OrderItemView(it.product.id!!, it.product.shortCode, it.quantity, it.priceAtPurchase, it.costAtPurchase) })

data class PageView<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
data class LiveSessionRequest(@field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 100) val tiktokRoomId: String? = null, @field:Size(max = 5000) val notes: String? = null)
data class LiveSessionView(val id: Long, val title: String, val tiktokRoomId: String?, val startTime: Instant,
    val endTime: Instant?, val totalRevenue: BigDecimal, val totalOrders: Int, val notes: String?)
fun LiveSession.toView() = LiveSessionView(id!!, title, tiktokRoomId, startTime, endTime, totalRevenue, totalOrders, notes)
