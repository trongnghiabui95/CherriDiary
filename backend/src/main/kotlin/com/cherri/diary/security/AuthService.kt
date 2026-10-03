package com.cherri.diary.security

import com.cherri.diary.api.*
import com.cherri.diary.domain.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Service
class AuthService(private val users: UserRepository, private val passwords: PasswordEncoder,
    private val encoder: JwtEncoder, @param:Value("\${app.jwt-ttl-seconds}") private val ttl: Long) {
    private data class Attempts(var count: Int, val start: Instant)
    private val attempts = ConcurrentHashMap<String, Attempts>()
    private val dummyHash = passwords.encode("a-dummy-password-for-timing")

    @Transactional(readOnly = true)
    fun login(request: LoginRequest, remoteAddress: String): LoginResponse {
        synchronized(attempts) {
            val now = Instant.now()
            attempts.entries.removeIf { it.value.start.plusSeconds(60).isBefore(now) }
            if (attempts.size >= 10000 && !attempts.containsKey(remoteAddress)) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Thử lại sau")
            val bucket = attempts.computeIfAbsent(remoteAddress) { Attempts(0, now) }
            if (++bucket.count > 10) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "Đăng nhập quá nhiều lần. Thử lại sau một phút.")
        }
        val user = users.findByUsername(request.username.trim().lowercase(Locale.ROOT))
        val correct = passwords.matches(request.password, user?.password ?: dummyHash)
        if (user == null || !user.isActive || !correct) throw ApiException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu")
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().issuer("cherri-diary").subject(user.id!!.toString())
            .issuedAt(now).expiresAt(now.plusSeconds(ttl)).claim("username", user.username).build()
        val token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        return LoginResponse(accessToken = token, expiresIn = ttl, user = user.toView())
    }
}

@Component
class AdminBootstrap(private val users: UserRepository, private val passwords: PasswordEncoder,
    @param:Value("\${app.bootstrap.username}") private val username: String,
    @param:Value("\${app.bootstrap.password}") private val password: String,
    @param:Value("\${app.bootstrap.minimum-password-length:12}") private val minimumPasswordLength: Int) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (password.isBlank()) return
        val normalized = username.trim().lowercase(Locale.ROOT)
        require(normalized.matches(Regex("[a-z0-9_.-]{3,80}"))) { "Invalid ADMIN_USERNAME" }
        if (users.findByUsername(normalized) != null) return
        require(minimumPasswordLength >= 1 && password.length >= minimumPasswordLength && password.toByteArray(Charsets.UTF_8).size <= 72) {
            "ADMIN_PASSWORD must contain $minimumPasswordLength+ characters and at most 72 UTF-8 bytes"
        }
        users.save(User(normalized, passwords.encode(password), "Quản trị Cherri Diary", Role.ROLE_ADMIN))
    }
}
