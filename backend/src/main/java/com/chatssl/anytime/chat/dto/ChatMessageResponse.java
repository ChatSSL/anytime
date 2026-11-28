package com.chatssl.anytime.chat.dto;

import java.time.LocalDateTime;

import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.User;

/** 채팅 메시지 단건 응답 (sender가 null이면 시스템 메시지) */
public record ChatMessageResponse(
    Long id,
    Long chatId,
    Long senderId,
    String senderNick,
    boolean system,
    boolean mine,
    String text,
    LocalDateTime createdAt
) {

    public static ChatMessageResponse of(Message message, Long viewerId) {
        User sender = message.getSender();
        boolean system = sender == null;
        Long senderId = system ? null : sender.getId();
        String senderNick = system ? null : sender.getNick();
        boolean mine = !system && senderId.equals(viewerId);
        return new ChatMessageResponse(
            message.getId(),
            message.getChat().getId(),
            senderId,
            senderNick,
            system,
            mine,
            message.getText(),
            message.getCreatedAt()
        );
    }
}
