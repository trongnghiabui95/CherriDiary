package com.cherri.diary.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Cherri Diary API").version("v1")
            .description("Đăng nhập tại /api/v1/auth/login, sau đó nhập accessToken vào Authorize để thử API."))
        .components(Components().addSecuritySchemes("bearerAuth", SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))

    @Bean
    fun publicLogin(): OpenApiCustomizer = OpenApiCustomizer { api ->
        api.paths["/api/v1/auth/login"]?.post?.security = emptyList()
    }
}
