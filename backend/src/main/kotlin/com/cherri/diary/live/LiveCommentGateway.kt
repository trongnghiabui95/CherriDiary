package com.cherri.diary.live

import com.cherri.diary.api.*
import com.cherri.diary.domain.LiveSessionRepository
import com.cherri.diary.domain.UserRepository
import com.cherri.diary.security.StaffPrincipal
import tools.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.socket.*
import org.springframework.web.socket.config.annotation.*
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class IngestCommentRequest(@field:NotBlank @field:Size(max = 100) val eventId: String,
    @field:NotBlank @field:Size(max = 4000) val comment: String, @field:Size(max = 100) val tiktokId: String? = null,
    @field:Size(max = 150) val nickname: String? = null)
data class LiveComment(val eventId: String, val liveSessionId: Long, val comment: String, val tiktokId: String?, val nickname: String? = null)

// Provider-independent boundary: an authorized connector forwards provider comments here.
// This does not pretend to be an official TikTok comment API.
@Component
class LiveCommentGateway(private val json: ObjectMapper, private val sessions: LiveSessionRepository,
    private val users: UserRepository) : TextWebSocketHandler() {
    private data class Subscriber(val liveSessionId: Long, val session: WebSocketSession)
    private val subscribers = ConcurrentHashMap<String, Subscriber>()
    private val recent = linkedMapOf<String, Instant>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val id = session.uri?.let { UriComponentsBuilder.fromUri(it).build().queryParams.getFirst("liveSessionId")?.toLongOrNull() }
        if (session.principal == null || id == null || !sessions.existsById(id)) {
            session.close(CloseStatus.POLICY_VIOLATION); return
        }
        session.textMessageSizeLimit = 8192
        subscribers[session.id] = Subscriber(id, ConcurrentWebSocketSessionDecorator(session, 5000, 65536))
    }
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Websocket is read-only; inbound provider events must use the validated REST endpoint.
        session.close(CloseStatus.POLICY_VIOLATION)
    }
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) { subscribers.remove(session.id) }

    @Scheduled(fixedDelay = 60000)
    fun closeExpiredSubscribers() {
        subscribers.values.forEach { subscriber ->
            val staff = (subscriber.session.principal as? Authentication)?.principal as? StaffPrincipal
            if (staff == null || !staff.expiresAt.isAfter(Instant.now()) || users.findById(staff.id).orElse(null)?.isActive != true) {
                subscribers.remove(subscriber.session.id)
                runCatching { subscriber.session.close(CloseStatus.POLICY_VIOLATION) }
            }
        }
    }

    @Transactional(readOnly = true)
    fun publish(liveSessionId: Long, request: IngestCommentRequest): LiveComment {
        val live = sessions.findById(liveSessionId).orElse(null) ?: missing("Không tìm thấy phiên live")
        if (live.endTime != null) conflict("Phiên live đã kết thúc")
        val event = LiveComment(request.eventId, liveSessionId, request.comment, com.cherri.diary.service.Identity.tiktok(request.tiktokId), request.nickname?.trim()?.takeIf { it.isNotEmpty() })
        synchronized(recent) {
            val now = Instant.now()
            recent.entries.removeIf { it.value.plusSeconds(300).isBefore(now) }
            val key = "$liveSessionId:${request.eventId}"
            if (recent.containsKey(key)) return event
            if (recent.size >= 10000) recent.remove(recent.keys.first())
            recent[key] = now
        }
        val message = TextMessage(json.writeValueAsString(event))
        subscribers.values.filter { it.liveSessionId == liveSessionId }.forEach { subscriber ->
            runCatching { subscriber.session.sendMessage(message) }.onFailure {
                subscribers.remove(subscriber.session.id)
                runCatching { subscriber.session.close(CloseStatus.SERVER_ERROR) }
            }
        }
        return event
    }
}

@Configuration
@EnableWebSocket
@EnableScheduling
class LiveWebSocketConfig(private val gateway: LiveCommentGateway) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(gateway, "/api/v1/live-comments") // JWT applies to the upgrade HTTP request.
    }
}

@RestController
@RequestMapping("/api/v1/live-sessions/{id}/comments")
class LiveCommentController(private val gateway: LiveCommentGateway) {
    @PostMapping fun ingest(@PathVariable id: Long, @Valid @RequestBody request: IngestCommentRequest) = gateway.publish(id, request)
}
