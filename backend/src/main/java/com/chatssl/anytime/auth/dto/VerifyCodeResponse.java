package com.chatssl.anytime.auth.dto;

/** newUser가 true면 프론트는 프로필 설정 화면(/profile)으로 보낸다. */
public record VerifyCodeResponse(Long userId, boolean newUser) {
}
