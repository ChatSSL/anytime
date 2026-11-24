package com.chatssl.anytime.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.auth.dto.TokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 로컬 개발 전용: 이메일 인증 없이 시드 사용자 토큰을 받는다.
 * app.auth.dev-login=true 일 때만 생긴다 (local 프로필 기본값 true, 서버(prod)에는 없음).
 * 예전 X-User-Id 헤더 대신 이걸로 다른 사용자를 골라 테스트한다.
 */
@Tag(name = "인증")
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "app.auth.dev-login", havingValue = "true")
public class DevLoginController {

    private final TokenService tokenService;

    public DevLoginController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record DevLoginRequest(@NotNull Long userId) {
    }

    @Operation(summary = "[로컬 전용] 시드 사용자로 로그인",
        description = "userId 1이 데모유저. 받은 accessToken을 Swagger 오른쪽 위 Authorize에 넣는다.")
    @PostMapping("/dev-login")
    public TokenResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        tokenService.checkActive(request.userId());
        return tokenService.issue(request.userId());
    }
}
