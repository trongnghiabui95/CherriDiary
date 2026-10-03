package com.cherri.diary.security

import com.cherri.diary.domain.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun jwtKey(@Value("\${app.jwt-secret}") secret: String): SecretKey {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) { "JWT_SECRET must contain at least 32 bytes" }
        return SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    }
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
    @Bean fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))
    @Bean fun jwtDecoder(key: SecretKey): JwtDecoder = NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256).build().also { decoder ->
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cherri-diary"))
        }
    @Bean
    fun securityFilterChain(http: HttpSecurity, decoder: JwtDecoder, users: UserRepository): SecurityFilterChain {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/login", "/error").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"status":401,"message":"Cần đăng nhập"}""")
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"status":403,"message":"Không có quyền thực hiện"}""")
                }
            }
            .addFilterBefore(JwtAuthenticationFilter(decoder, users), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
