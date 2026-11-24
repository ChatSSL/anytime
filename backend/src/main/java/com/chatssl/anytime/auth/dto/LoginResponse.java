package com.chatssl.anytime.auth.dto;

/**
 * 이메일 인증 성공 응답. newUser가 true면 프론트는 프로필 설정 화면으로 보낸다.
 * 이후 요청은 Authorization: Bearer {accessToken}
 */
public record LoginResponse(Long userId, boolean newUser, String accessToken, String refreshToken, long expiresIn) {

    public static LoginResponse of(VerifyCodeResponse verified, TokenResponse tokens) {
        return new LoginResponse(verified.userId(), verified.newUser(),
            tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
