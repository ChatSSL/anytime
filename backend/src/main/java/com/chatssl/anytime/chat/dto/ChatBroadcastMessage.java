package com.chatssl.anytime.chat.dto;

import java.time.LocalDateTime;

import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.User;

/**
 * STOMP /topic/chat.{chatId} 브로드캐스트 메시지 페이로드.
 * 모든 구독자에게 동일한 JSON이 전달되므로 수신자별 mine 필드 대신 senderId를 내려주고,
 * 프론트에서 senderId === myUserId로 내 말풍선 여부를 판별한다.
 */
public record ChatBroadcastMessage(
    Long id,
    Long chatId,
    Long senderId,
    String senderNick,
    boolean system,
    String text,
    LocalDateTime createdAt
) {

    public static ChatBroadcastMessage of(Message message, LocalDateTime fallbackCreatedAt) {
        User sender = message.getSender();
        boolean system = sender == null;
        return new ChatBroadcastMessage(
            message.getId(),
            message.getChat().getId(),
            system ? null : sender.getId(),
            system ? null : sender.getNick(),
            system,
            message.getText(),
            message.getCreatedAt() != null ? message.getCreatedAt() : fallbackCreatedAt
        );
    }
}
