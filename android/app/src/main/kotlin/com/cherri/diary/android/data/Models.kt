package com.cherri.diary.android.data

import java.math.BigDecimal

data class LoginRequest(val username: String, val password: String)
data class UserView(val id: Long, val username: String, val fullName: String, val role: String)
data class LoginResponse(val accessToken: String, val expiresIn: Long, val user: UserView)
data class ProductView(val id: Long, val shortCode: String, val name: String, val sellingPrice: BigDecimal, val stockQuantity: Int, val status: String)
data class CustomerView(val id: Long, val name: String, val phoneNumber: String?, val tiktokId: String?, val address: String?, val isBlacklisted: Boolean)
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
data class LiveComment(val eventId: String, val liveSessionId: Long, val comment: String, val tiktokId: String?)
