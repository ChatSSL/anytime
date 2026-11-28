package com.chatssl.anytime.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.chatssl.anytime.common.CurrentUserId;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    static {
        // @CurrentUserId Long userId 는 토큰에서 꺼내는 값이라 Swagger 입력 칸에서 숨긴다 (17단계 정리)
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI anytimeOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Anytime API")
                .version("v1")
                .description("에러 응답은 모두 {code, message}. "
                    + "로그인이 필요한 API는 오른쪽 위 Authorize에 액세스 토큰을 넣는다. "
                    + "로컬에서는 POST /api/auth/dev-login {\"userId\": 1} 로 바로 토큰을 받을 수 있다."))
            .components(new Components().addSecuritySchemes(BEARER,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
