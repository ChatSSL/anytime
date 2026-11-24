package com.chatssl.anytime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Swagger: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI anytimeOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Anytime API")
            .version("v1")
            .description("에러 응답은 모두 {code, message}. 로그인 전에는 X-User-Id 헤더로 사용자를 고른다(없으면 1 = 데모유저)."));
    }
}
