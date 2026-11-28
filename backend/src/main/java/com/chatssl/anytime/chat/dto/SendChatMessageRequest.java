package com.chatssl.anytime.chat.dto;

/**
 * STOMP /app/chat.send 요청 페이로드.
 * 보안 원칙: senderId는 클라이언트가 보낼 수 없으며, 서버에서 인증된 Principal로부터만 추출한다.
 */
public record SendChatMessageRequest(
    Long chatId,
    String text
) {
}
