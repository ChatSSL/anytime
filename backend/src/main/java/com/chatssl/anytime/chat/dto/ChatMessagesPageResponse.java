package com.chatssl.anytime.chat.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.chatssl.anytime.domain.Message;

/** GET /api/chats/{id}/messages 페이지 응답 (최신순 정렬: id DESC) */
public record ChatMessagesPageResponse(
    Long chatId,
    List<ChatMessageResponse> messages,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {

    public static ChatMessagesPageResponse of(Long chatId, Page<Message> messagePage, Long viewerId) {
        List<ChatMessageResponse> items = messagePage.getContent().stream()
            .map(m -> ChatMessageResponse.of(m, viewerId))
            .toList();
        return new ChatMessagesPageResponse(
            chatId,
            items,
            messagePage.getNumber(),
            messagePage.getSize(),
            messagePage.getTotalElements(),
            messagePage.getTotalPages(),
            messagePage.hasNext()
        );
    }
}
