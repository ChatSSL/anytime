package com.chatssl.anytime.chat.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import com.chatssl.anytime.domain.AiMission;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.user.dto.UserProfileResponse;

/** GET /api/chats, PATCH /api/chats/{id}/link 응답 */
public record ChatSummaryResponse(
    Long id,
    String kind,
    Long roomId,
    ChatRoomInfo room,
    UserProfileResponse otherUser,
    String link,
    AiMission mission,
    boolean missionDone,
    ChatMessageResponse lastMessage,
    long unreadCount,
    LocalDateTime lastActivityAt
) {

    public static ChatSummaryResponse of(
        Chat chat,
        ChatRoomInfo room,
        UserProfileResponse otherUser,
        ChatMessageResponse lastMessage,
        long unreadCount
    ) {
        LocalDateTime activityAt = lastMessage != null && lastMessage.createdAt() != null
            ? lastMessage.createdAt()
            : chat.getCreatedAt();
        return new ChatSummaryResponse(
            chat.getId(),
            chat.getKind().name().toLowerCase(Locale.ROOT),
            chat.getRoom() == null ? null : chat.getRoom().getId(),
            room,
            otherUser,
            chat.getLink(),
            chat.getMission(),
            chat.isMissionDone(),
            lastMessage,
            unreadCount,
            activityAt
        );
    }
}
