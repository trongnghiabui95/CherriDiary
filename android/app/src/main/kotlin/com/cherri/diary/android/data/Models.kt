package com.cherri.diary.android.data

import java.math.BigDecimal

data class LoginRequest(val username: String, val password: String)
data class UserView(val id: Long, val username: String, val fullName: String, val role: String, val isActive: Boolean = true)
data class LoginResponse(val accessToken: String, val expiresIn: Long, val user: UserView)
data class ProductView(val id: Long, val shortCode: String, val name: String, val sellingPrice: BigDecimal, val stockQuantity: Int, val status: String,
    val costPrice: BigDecimal? = null, val imageUrl: String? = null, val description: String? = null, val categoryId: Long? = null)
data class CustomerView(val id: Long, val name: String, val phoneNumber: String?, val tiktokId: String?, val address: String?, val isBlacklisted: Boolean, val notes: String? = null)

data class ProductRequest(val name: String, val shortCode: String, val costPrice: BigDecimal, val sellingPrice: BigDecimal,
    val stockQuantity: Int, val imageUrl: String? = null, val status: String = "ACTIVE", val description: String? = null, val categoryId: Long? = null)
data class BlacklistEntryRequest(val name: String?, val phoneNumber: String?, val tiktokId: String?, val notes: String, val id: Long? = null)
data class BlacklistRequest(val isBlacklisted: Boolean, val notes: String?)
data class UserRequest(val username: String, val fullName: String, val password: String, val role: String)
data class EditUserRequest(val fullName: String, val role: String, val isActive: Boolean, val password: String?)
data class ProductImageView(val imageUrl: String)
data class ParsedItem(val shortCode: String, val quantity: Int, val product: ProductView?)
data class ParsedComment(val commentRaw: String, val phoneNumber: String?, val tiktokId: String?,
    val customer: CustomerView?, val items: List<ParsedItem>, val isBlacklisted: Boolean, val warnings: List<String>)
data class ParseCommentRequest(val comment: String)
data class QuickOrderItem(val shortCode: String, val quantity: Int)
data class FastCreateRequest(val requestId: String, val items: List<QuickOrderItem>, val customerId: Long? = null,
    val customerName: String? = null, val phoneNumber: String? = null, val tiktokId: String? = null,
    val facebookId: String? = null, val address: String? = null, val liveSessionId: Long? = null,
    val channel: String = "TIKTOK", val depositAmount: BigDecimal = BigDecimal.ZERO,
    val shippingFee: BigDecimal = BigDecimal.ZERO, val paymentMethod: String = "COD",
    val commentRaw: String? = null, val acknowledgeBlacklist: Boolean = false)
data class OrderItemView(val shortCode: String, val quantity: Int, val priceAtPurchase: BigDecimal)
data class OrderView(val id: Long, val orderCode: String, val customer: CustomerView, val channel: String,
    val subtotalAmount: BigDecimal, val depositAmount: BigDecimal, val shippingFee: BigDecimal,
    val totalAmount: BigDecimal, val remainingAmount: BigDecimal, val paidAmount: BigDecimal,
    val status: String, val paymentStatus: String, val paymentMethod: String, val trackingCode: String?,
    val proofImageUrl: String?, val commentRaw: String?, val items: List<OrderItemView>)
data class PageView<T>(val content: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int)
data class StatusRequest(val status: String)
data class PaymentRequest(val depositAmount: BigDecimal, val paidAmount: BigDecimal, val paymentMethod: String)
data class TrackingRequest(val trackingCode: String)
data class LiveSessionView(val id: Long, val title: String, val tiktokRoomId: String?)
data class LiveSessionRequest(val title: String, val tiktokRoomId: String?)
data class LiveComment(val eventId: String, val liveSessionId: Long, val comment: String, val tiktokId: String?, val nickname: String? = null)
data class LiveSourceRequest(val liveSessionId: Long, val username: String)
data class LiveSourceView(val enabled: Boolean, val liveSessionId: Long?, val username: String?)
