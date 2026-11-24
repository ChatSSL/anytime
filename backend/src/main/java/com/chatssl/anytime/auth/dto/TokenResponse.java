package com.chatssl.anytime.auth.dto;

/** expiresIn: 액세스 토큰 유효 시간(초) */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
