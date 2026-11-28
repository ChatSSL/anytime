package com.chatssl.anytime.chat.dto;

import jakarta.validation.constraints.Size;

/** PATCH /api/chats/{id}/link 요청 (빈 문자열 또는 null이면 링크 제거) */
public record UpdateChatLinkRequest(
    @Size(max = 255, message = "링크는 255자까지 쓸 수 있어요")
    String link
) {
}
