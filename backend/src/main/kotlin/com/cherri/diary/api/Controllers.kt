package com.cherri.diary.api

import com.cherri.diary.domain.*
import com.cherri.diary.security.*
import com.cherri.diary.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, servlet: HttpServletRequest) = auth.login(request, servlet.remoteAddr)
}

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val orders: OrderService, private val parser: CommentParserService) {
    @PostMapping("/parse-comment")
    fun parse(@Valid @RequestBody request: ParseCommentRequest) = parser.parse(request.comment)
    @PostMapping("/fast-create", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(@AuthenticationPrincipal staff: StaffPrincipal,
        @Valid @RequestPart("order") request: FastCreateRequest,
        @RequestPart("proof", required = false) proof: MultipartFile?) = orders.fastCreate(staff.id, request, proof)
    @GetMapping
    fun list(@RequestParam(required = false) phone: String?, @RequestParam(required = false) tiktokId: String?,
        @RequestParam(required = false) orderCode: String?, @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) channel: Channel?, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int) = orders.list(phone, tiktokId, orderCode, status, channel, page, size)
    @GetMapping("/{id}") fun get(@PathVariable id: Long) = orders.get(id)
    @PutMapping("/{id}/status") fun status(@PathVariable id: Long, @Valid @RequestBody request: StatusRequest) = orders.updateStatus(id, request.status)
    @PutMapping("/{id}/payment") fun payment(@PathVariable id: Long, @Valid @RequestBody request: PaymentRequest) = orders.updatePayment(id, request)
    @PutMapping("/{id}/tracking") fun tracking(@PathVariable id: Long, @Valid @RequestBody request: TrackingRequest) = orders.updateTracking(id, request)
}

@RestController
@RequestMapping("/api/v1")
class CatalogController(private val catalog: CatalogService, private val proofs: ProofStorageService) {
    @GetMapping("/products") fun products(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = catalog.products(page, size)
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/products")
    fun createProduct(@Valid @RequestBody request: ProductRequest) = catalog.saveProduct(null, request)
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/products/{id}")
    fun updateProduct(@PathVariable id: Long, @Valid @RequestBody request: ProductRequest) = catalog.saveProduct(id, request)
    @GetMapping("/categories") fun categories() = catalog.categories()
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/categories")
    fun category(@Valid @RequestBody request: CategoryRequest) = catalog.category(request)
    @GetMapping("/customers") fun customers(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = catalog.customers(page, size)
    @PostMapping("/customers") fun customer(@Valid @RequestBody request: CustomerRequest) = catalog.createCustomer(request)
    @PreAuthorize("hasRole('ADMIN')") @PutMapping("/customers/{id}/blacklist")
    fun blacklist(@PathVariable id: Long, @Valid @RequestBody request: BlacklistRequest) = catalog.blacklist(id, request)
    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/users")
    fun user(@Valid @RequestBody request: UserRequest) = catalog.user(request)
    @GetMapping("/live-sessions") fun sessions(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = catalog.liveSessions(page, size)
    @PostMapping("/live-sessions") fun createSession(@Valid @RequestBody request: LiveSessionRequest) = catalog.createSession(request)
    @PutMapping("/live-sessions/{id}/end") fun endSession(@PathVariable id: Long) = catalog.endSession(id)
    @GetMapping("/proofs/{name}")
    fun proof(@PathVariable name: String) = ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG).body(proofs.load(name))
}
