package com.cherri.diary.security

import com.cherri.diary.domain.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

data class StaffPrincipal(val id: Long, val username: String, val expiresAt: Instant)

class JwtAuthenticationFilter(private val decoder: JwtDecoder, private val users: UserRepository) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw JwtException("Invalid scheme")
                val jwt = decoder.decode(header.removePrefix("Bearer "))
                val id = jwt.subject.toLongOrNull() ?: throw JwtException("Invalid subject")
                val user = users.findById(id).orElse(null)
                if (user == null || !user.isActive) throw JwtException("Inactive user")
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    StaffPrincipal(id, user.username, jwt.expiresAt ?: throw JwtException("Missing expiration")), null, listOf(SimpleGrantedAuthority(user.role.name)))
            } catch (_: JwtException) {
                SecurityContextHolder.clearContext()
                response.status = 401
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("""{"status":401,"message":"Token không hợp lệ hoặc đã hết hạn"}""")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
