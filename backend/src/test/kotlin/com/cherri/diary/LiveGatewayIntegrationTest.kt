package com.cherri.diary

import com.cherri.diary.api.LoginRequest
import com.cherri.diary.domain.*
import com.cherri.diary.live.IngestCommentRequest
import com.cherri.diary.live.LiveCommentGateway
import com.cherri.diary.security.AuthService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfiguration::class)
class LiveGatewayIntegrationTest {
    @LocalServerPort var port = 0
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var sessions: LiveSessionRepository
    @Autowired lateinit var passwords: PasswordEncoder
    @Autowired lateinit var auth: AuthService
    @Autowired lateinit var gateway: LiveCommentGateway

    @Test
    fun `authenticated websocket receives its session comments once and revocation closes it`() {
        val user = users.saveAndFlush(User("gateway", requireNotNull(passwords.encode("test-password-12345")), "Gateway"))
        val live = sessions.saveAndFlush(LiveSession(title = "Live A"))
        val other = sessions.saveAndFlush(LiveSession(title = "Live B"))
        val token = auth.login(LoginRequest("gateway", "test-password-12345"), "gateway-test").accessToken
        val messages = LinkedBlockingQueue<String>()
        val closed = CompletableFuture<Int>()
        val listener = object : WebSocket.Listener {
            private val buffer = StringBuilder()
            override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                buffer.append(data)
                if (last) { messages.add(buffer.toString()); buffer.clear() }
                webSocket.request(1)
                return null
            }
            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                closed.complete(statusCode); return null
            }
        }
        val url = URI("ws://localhost:$port/api/v1/live-comments?liveSessionId=${live.id}")
        val client = HttpClient.newHttpClient()
        assertThatThrownBy { client.newWebSocketBuilder().buildAsync(url, listener).get(5, TimeUnit.SECONDS) }
            .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException::class.java)
        val socket = client.newWebSocketBuilder().header("Authorization", "Bearer $token").buildAsync(url, listener).get(5, TimeUnit.SECONDS)
        try {
            gateway.publish(other.id!!, IngestCommentRequest("other-event", "A1 1"))
            assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).isNull()
            val event = IngestCommentRequest("event-1", "0987654321 A1 2", "nick")
            gateway.publish(live.id!!, event)
            assertThat(messages.poll(5, TimeUnit.SECONDS)).contains("event-1", "0987654321 A1 2")
            gateway.publish(live.id!!, event)
            assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).isNull()
            users.saveAndFlush(users.findById(user.id!!).get().apply { isActive = false })
            gateway.closeExpiredSubscribers()
            assertThat(closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008)
        } finally { socket.abort() }
    }
}
