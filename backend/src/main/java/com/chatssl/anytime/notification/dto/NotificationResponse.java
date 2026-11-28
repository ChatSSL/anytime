package com.chatssl.anytime.notification.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import com.chatssl.anytime.domain.Notification;

/**
 * 알림 한 개. 프론트 Notif와 같은 모양으로 소문자 값을 쓴다.
 * type: reco, join, request, reminder, change, cancel, rate / resolved: accepted, declined, null
 */
public record NotificationResponse(
    Long id,
    String type,
    String title,
    String body,
    String link,
    /** 관련된 사람 (1:1 요청 보낸 사람 등). 없으면 null */
    Long relatedUserId,
    boolean needsAction,
    String resolved,
    boolean read,
    LocalDateTime createdAt
) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getType().name().toLowerCase(Locale.ROOT),
            n.getTitle(),
            n.getBody(),
            n.getLink(),
            n.getRelatedUser() == null ? null : n.getRelatedUser().getId(),
            n.isNeedsAction(),
            n.getResolved() == null ? null : n.getResolved().name().toLowerCase(Locale.ROOT),
            n.getReadAt() != null,
            n.getCreatedAt()
        );
    }
}
