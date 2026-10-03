package com.cherri.diary

import com.cherri.diary.api.*
import com.cherri.diary.domain.*
import com.cherri.diary.service.*
import com.cherri.diary.security.AdminBootstrap
import org.springframework.boot.DefaultApplicationArguments
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration::class)
class OrderIntegrationTest {
    @Autowired lateinit var orders: OrderRepository
    @Autowired lateinit var products: ProductRepository
    @Autowired lateinit var customers: CustomerRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var sessions: LiveSessionRepository
    @Autowired lateinit var categories: CategoryRepository
    @Autowired lateinit var orderService: OrderService
    @Autowired lateinit var parser: CommentParserService
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var passwords: PasswordEncoder
    private lateinit var staff: User
    private lateinit var admin: User
    private lateinit var product: Product
    private val password = "test-password-12345"

    @BeforeEach
    fun setup() {
        orders.deleteAll(); products.deleteAll(); categories.deleteAll(); customers.deleteAll(); sessions.deleteAll(); users.deleteAll()
        val hash = passwords.encode(password)
        staff = users.saveAndFlush(User("staff", hash, "Staff", Role.ROLE_STAFF))
        admin = users.saveAndFlush(User("admin", hash, "Admin", Role.ROLE_ADMIN))
        product = products.saveAndFlush(Product(name = "Áo", shortCode = "A1", costPrice = BigDecimal("40.00"), sellingPrice = BigDecimal("100.00"), stockQuantity = 10))
    }
    private fun request(quantity: Int = 2, phone: String = "0987654321", id: UUID = UUID.randomUUID()) =
        FastCreateRequest(id, listOf(QuickOrderItem("a1", quantity)), phoneNumber = phone,
            depositAmount = BigDecimal("50.00"), shippingFee = BigDecimal("20.00"), commentRaw = "$phone A1 $quantity")
    private fun token(username: String = "staff"): String {
        val response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(LoginRequest(username, password)))).andExpect(status().isOk).andReturn().response
        return json.readTree(response.contentAsString)["accessToken"].asText()
    }

    @Test
    fun `swagger is public documents bearer auth and keeps business APIs protected`() {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk)
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty)
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray)
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrap allows short local password preserves existing account and rejects short production password`() {
        val arguments = DefaultApplicationArguments()
        val existingHash = users.findByUsername("admin")!!.password
        AdminBootstrap(users, passwords, "admin", "short", 12).run(arguments)
        assertThat(users.findByUsername("admin")!!.password).isEqualTo(existingHash)
        AdminBootstrap(users, passwords, "localadmin", "short", 1).run(arguments)
        assertThat(passwords.matches("short", users.findByUsername("localadmin")!!.password)).isTrue()
        assertThatThrownBy { AdminBootstrap(users, passwords, "productionadmin", "short", 12).run(arguments) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(users.findByUsername("productionadmin")).isNull()
    }

    @Test
    fun `parser recognizes phone nick quantities and blacklist from either identity`() {
        customers.saveAndFlush(Customer("Khách", "0987654321", "nicktiktok", isBlacklisted = true))
        val phone = parser.parse("0987654321 a1 2")
        assertThat(phone.phoneNumber).isEqualTo("0987654321")
        assertThat(phone.items.single().quantity).isEqualTo(2)
        assertThat(phone.isBlacklisted).isTrue()
        val nick = parser.parse("@nicktiktok A1")
        assertThat(nick.tiktokId).isEqualTo("nicktiktok")
        assertThat(nick.items.single().quantity).isEqualTo(1)
        assertThat(nick.isBlacklisted).isTrue()
        assertThat(parser.parse("+84987654321 A1 0").warnings).isNotEmpty()
        assertThat(parser.parse("0987654321 A1 -2").items).isEmpty()
        assertThat(parser.parse("0987654321 A1 2.5").items).isEmpty()
        assertThat(parser.parse("@nicktiktok UNKNOWN2 1").items.single().product).isNull()
        assertThat(parser.parse("0987654321 0912345678 A1 1").phoneNumber).isNull()
    }

    @Test
    fun `snapshots prices calculates COD and replay never reduces stock twice`() {
        val input = request()
        val created = orderService.fastCreate(staff.id!!, input, null)
        assertThat(created.subtotalAmount).isEqualByComparingTo("200.00")
        assertThat(created.totalCost).isEqualByComparingTo("80.00")
        assertThat(created.totalAmount).isEqualByComparingTo("170.00")
        assertThat(created.remainingAmount).isEqualByComparingTo("170.00")
        assertThat(created.userId).isEqualTo(staff.id)
        assertThat(orderService.fastCreate(staff.id!!, input, null).id).isEqualTo(created.id)
        assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(8)
        val updated = products.findById(product.id!!).get().apply { sellingPrice = BigDecimal("999.00"); costPrice = BigDecimal("888.00") }
        products.saveAndFlush(updated)
        assertThat(orderService.get(created.id).items.single().priceAtPurchase).isEqualByComparingTo("100.00")
        assertThat(orderService.get(created.id).totalCost).isEqualByComparingTo("80.00")
    }

    @Test
    fun `cancel restores stock and counters exactly once and cannot reopen`() {
        val session = sessions.saveAndFlush(LiveSession(title = "Live"))
        val created = orderService.fastCreate(staff.id!!, request().copy(liveSessionId = session.id), null)
        orderService.updateStatus(created.id, OrderStatus.CANCELLED)
        orderService.updateStatus(created.id, OrderStatus.CANCELLED)
        assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(10)
        assertThat(customers.findById(created.customer.id).get().totalOrders).isZero()
        assertThat(sessions.findById(session.id!!).get().totalOrders).isZero()
        assertThat(sessions.findById(session.id!!).get().totalRevenue).isEqualByComparingTo("0")
        assertThatThrownBy { orderService.updateStatus(created.id, OrderStatus.CONFIRMED) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `payment updates are absolute and invalid amounts rollback`() {
        val created = orderService.fastCreate(staff.id!!, request(), null)
        assertThatThrownBy { orderService.updatePayment(created.id, PaymentRequest(BigDecimal("999.00"), BigDecimal.ZERO, PaymentMethod.COD)) }.isInstanceOf(ApiException::class.java)
        assertThat(orderService.get(created.id).depositAmount).isEqualByComparingTo("50.00")
        orderService.updateStatus(created.id, OrderStatus.SHIPPING)
        assertThatThrownBy { orderService.updateStatus(created.id, OrderStatus.COMPLETED) }.isInstanceOf(ApiException::class.java)
        val payment = PaymentRequest(BigDecimal("50.00"), BigDecimal("170.00"), PaymentMethod.COD)
        orderService.updatePayment(created.id, payment); orderService.updatePayment(created.id, payment)
        assertThat(orderService.get(created.id).remainingAmount).isEqualByComparingTo("0")
        assertThat(orderService.updateStatus(created.id, OrderStatus.COMPLETED).paymentStatus).isEqualTo(PaymentStatus.PAID)
        assertThatThrownBy { orderService.updateStatus(created.id, OrderStatus.CANCELLED) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `failed basket rolls back every stock change`() {
        products.saveAndFlush(Product(name = "Váy", shortCode = "V2", sellingPrice = BigDecimal("100.00"), stockQuantity = 0))
        assertThatThrownBy { orderService.fastCreate(staff.id!!, request().copy(items = listOf(QuickOrderItem("A1", 2), QuickOrderItem("V2", 1))), null) }.isInstanceOf(ApiException::class.java)
        assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(10)
        assertThat(orders.count()).isZero()
        assertThat(customers.count()).isZero()
    }

    @Test
    fun `parallel staff cannot oversell last three units`() {
        products.saveAndFlush(products.findById(product.id!!).get().apply { stockQuantity = 3 })
        val staffIds = (0..7).map { users.saveAndFlush(User("staff$it", staff.password, "Staff $it")).id!! }
        val ready = CountDownLatch(8); val go = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = staffIds.mapIndexed { index, userId -> executor.submit(Callable {
                ready.countDown(); check(go.await(10, TimeUnit.SECONDS))
                runCatching { orderService.fastCreate(userId, request(1, "098765432$index"), null) }
            }) }
            check(ready.await(10, TimeUnit.SECONDS)); go.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertThat(outcomes.count { it.isSuccess }).isEqualTo(3)
            assertThat(outcomes.filter { it.isFailure }.map { it.exceptionOrNull() }).allMatch { it is ApiException }
            assertThat(products.findById(product.id!!).get().stockQuantity).isZero()
            assertThat(orders.count()).isEqualTo(3)
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `parallel requests for one customer keep counters consistent`() {
        val customer = customers.saveAndFlush(Customer("Khách", "0987654321"))
        val otherStaff = users.saveAndFlush(User("otherstaff", staff.password, "Staff"))
        val executor = Executors.newFixedThreadPool(2)
        val go = CountDownLatch(1)
        try {
            val futures = listOf(staff.id!!, otherStaff.id!!).map { userId -> executor.submit(Callable {
                check(go.await(10, TimeUnit.SECONDS))
                orderService.fastCreate(userId, request(1), null)
            }) }
            go.countDown()
            val created = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertThat(customers.findById(customer.id!!).get().totalOrders).isEqualTo(2)
            val cancellations = created.map { order -> executor.submit(Callable { orderService.updateStatus(order.id, OrderStatus.CANCELLED) }) }
            cancellations.forEach { it.get(30, TimeUnit.SECONDS) }
            assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(10)
            assertThat(customers.findById(customer.id!!).get().totalOrders).isZero()
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `parallel retries with the same request id create one order`() {
        val executor = Executors.newFixedThreadPool(2)
        val input = request()
        try {
            val futures = (1..2).map { executor.submit(Callable { orderService.fastCreate(staff.id!!, input, null) }) }
            val created = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertThat(created.map { it.id }.distinct()).hasSize(1)
            assertThat(orders.count()).isEqualTo(1)
            assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(8)
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `blacklist needs acknowledgement and conflicting identities are rejected`() {
        customers.saveAndFlush(Customer("Khách", "0987654321", "one", isBlacklisted = true))
        customers.saveAndFlush(Customer("Khác", "0912345678", "two"))
        assertThatThrownBy { orderService.fastCreate(staff.id!!, request(), null) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { orderService.fastCreate(staff.id!!, request().copy(tiktokId = "two", acknowledgeBlacklist = true), null) }.isInstanceOf(ApiException::class.java)
        assertThat(orderService.fastCreate(staff.id!!, request().copy(acknowledgeBlacklist = true), null).customer.isBlacklisted).isTrue()
    }

    @Test
    fun `JWT guards APIs enforces roles and multipart derives staff from token`() {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized)
        val staffToken = token()
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer $staffToken").contentType("application/json")
            .content(json.writeValueAsString(UserRequest("another", password, "User")))).andExpect(status().isForbidden)
        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer ${token("admin")}").contentType("application/json")
            .content(json.writeValueAsString(UserRequest("another", password, "User")))).andExpect(status().isOk)
        val orderPart = MockMultipartFile("order", "", "application/json", json.writeValueAsBytes(request()))
        val image = BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val proof = MockMultipartFile("proof", "../../evil.html", "text/html", bytes)
        val response = mvc.perform(multipart("/api/v1/orders/fast-create").file(orderPart).file(proof).header("Authorization", "Bearer $staffToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.userId").value(staff.id!!)).andReturn().response
        val proofUrl = json.readTree(response.contentAsString)["proofImageUrl"].asText()
        mvc.perform(get(proofUrl)).andExpect(status().isUnauthorized)
        mvc.perform(get(proofUrl).header("Authorization", "Bearer $staffToken")).andExpect(status().isOk).andExpect(content().contentType("image/png"))
        mvc.perform(get("/api/v1/orders").param("phone", "0987654321").header("Authorization", "Bearer $staffToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(1))
        users.saveAndFlush(users.findById(staff.id!!).get().apply { isActive = false })
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer $staffToken")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `invalid proof rolls back order and stock`() {
        val proof = MockMultipartFile("proof", "fake.png", "image/png", "not-an-image".toByteArray())
        assertThatThrownBy { orderService.fastCreate(staff.id!!, request(), proof) }.isInstanceOf(ApiException::class.java)
        assertThat(products.findById(product.id!!).get().stockQuantity).isEqualTo(10)
        assertThat(orders.count()).isZero()
    }
}
