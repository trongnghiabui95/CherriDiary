package com.cherri.diary.live

import com.cherri.diary.api.conflict
import com.cherri.diary.api.invalid
import com.cherri.diary.api.missing
import com.cherri.diary.domain.LiveSessionRepository
import jakarta.persistence.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@Entity
@Table(name = "live_connector_source")
class ConnectorSource(
    @Id var id: Long = 1,
    @Column(name = "live_session_id", nullable = false) var liveSessionId: Long = 0,
    @Column(nullable = false, length = 100) var username: String = ""
)

interface ConnectorSourceRepository : JpaRepository<ConnectorSource, Long>
data class SelectLiveSource(@field:Positive val liveSessionId: Long, @field:Size(max = 300) val username: String)
data class LiveSourceView(val enabled: Boolean, val liveSessionId: Long? = null, val username: String? = null)

@RestController
@RequestMapping("/api/v1/live-connector")
class LiveConnectorController(private val sources: ConnectorSourceRepository, private val sessions: LiveSessionRepository) {
    @GetMapping
    @Transactional(readOnly = true)
    fun get(): LiveSourceView {
        val source = sources.findById(1).orElse(null) ?: return LiveSourceView(false)
        val live = sessions.findById(source.liveSessionId).orElse(null)
        return LiveSourceView(live != null && live.endTime == null, source.liveSessionId, source.username)
    }

    @PostMapping
    @Transactional
    fun select(@Valid @RequestBody request: SelectLiveSource): LiveSourceView {
        val live = sessions.lockById(request.liveSessionId) ?: missing("Không tìm thấy phiên live")
        if (live.endTime != null) conflict("Phiên live đã kết thúc")
        val input = request.username.trim()
        val username = when {
            input.startsWith("https://www.tiktok.com/@") -> input.substringAfter("/@").substringBefore('/').substringBefore('?')
            input.startsWith("https://tiktok.com/@") -> input.substringAfter("/@").substringBefore('/').substringBefore('?')
            else -> input.removePrefix("@")
        }
        if (!username.matches(Regex("[A-Za-z0-9_.]{1,100}")) || username.all { it.isDigit() })
            invalid("Nhập @username TikTok hoặc link livestream; đây không phải ID phiên Cherri")
        sources.save(ConnectorSource(liveSessionId = live.id!!, username = username))
        return LiveSourceView(true, live.id, username)
    }
}
