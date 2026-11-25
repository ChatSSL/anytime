package com.chatssl.anytime.user.dto;

/** 닉네임 중복 확인 결과. available=false면 message에 이유가 있다 */
public record NickCheckResponse(boolean available, String message) {
}
