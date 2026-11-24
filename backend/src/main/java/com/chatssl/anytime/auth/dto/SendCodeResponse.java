package com.chatssl.anytime.auth.dto;

/** expiresIn: 인증번호 유효 시간(초), resendAfter: 재발송까지 기다릴 시간(초) */
public record SendCodeResponse(int expiresIn, int resendAfter) {
}
