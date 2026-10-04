package com.cherri.diary.android.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {
    @GET("users/me") suspend fun me(): UserView
    @GET("management/products") suspend fun manageProducts(@Query("q") q: String, @Query("page") page: Int): PageView<ProductView>
    @POST("products") suspend fun createProduct(@Body request: ProductRequest): ProductView
    @PUT("products/{id}") suspend fun editProduct(@Path("id") id: Long, @Body request: ProductRequest): ProductView
    @DELETE("products/{id}") suspend fun archiveProduct(@Path("id") id: Long): ProductView
    @Multipart @POST("management/product-images") suspend fun uploadProductImage(@Part image: MultipartBody.Part): ProductImageView
    @GET("management/blacklist") suspend fun blacklist(@Query("q") q: String, @Query("page") page: Int): PageView<CustomerView>
    @POST("management/blacklist") suspend fun saveBlacklist(@Body request: BlacklistEntryRequest): CustomerView
    @PUT("customers/{id}/blacklist") suspend fun unblacklist(@Path("id") id: Long, @Body request: BlacklistRequest): CustomerView
    @GET("users") suspend fun users(@Query("q") q: String, @Query("page") page: Int): PageView<UserView>
    @POST("users") suspend fun createUser(@Body request: UserRequest): UserView
    @PUT("users/{id}") suspend fun editUser(@Path("id") id: Long, @Body request: EditUserRequest): UserView
    @GET("live-connector") suspend fun liveSource(): LiveSourceView
    @POST("live-connector") suspend fun selectLiveSource(@Body request: LiveSourceRequest): LiveSourceView
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @POST("orders/parse-comment") suspend fun parseComment(@Body request: ParseCommentRequest): ParsedComment
    @Multipart @POST("orders/fast-create") suspend fun fastCreate(@Part("order") order: RequestBody, @Part proof: MultipartBody.Part?): OrderView
    @GET("orders") suspend fun orders(@Query("phone") phone: String? = null, @Query("tiktokId") tiktokId: String? = null,
        @Query("orderCode") orderCode: String? = null, @Query("status") status: String? = null,
        @Query("channel") channel: String? = null, @Query("page") page: Int = 0, @Query("size") size: Int = 20): PageView<OrderView>
    @PUT("orders/{id}/status") suspend fun status(@Path("id") id: Long, @Body request: StatusRequest): OrderView
    @PUT("orders/{id}/payment") suspend fun payment(@Path("id") id: Long, @Body request: PaymentRequest): OrderView
    @PUT("orders/{id}/tracking") suspend fun tracking(@Path("id") id: Long, @Body request: TrackingRequest): OrderView
    @GET("products") suspend fun products(@Query("page") page: Int = 0, @Query("size") size: Int = 100, @Query("q") q: String = ""): PageView<ProductView>
    @POST("live-sessions") suspend fun createSession(@Body request: LiveSessionRequest): LiveSessionView
    @PUT("live-sessions/{id}/end") suspend fun endSession(@Path("id") id: Long): LiveSessionView
}
