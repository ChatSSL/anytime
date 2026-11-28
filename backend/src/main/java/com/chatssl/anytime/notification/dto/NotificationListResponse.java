package com.chatssl.anytime.notification.dto;

import java.util.List;

/** GET /api/notifications 응답. unreadCount는 하단 탭·벨 아이콘 점 표시용 */
public record NotificationListResponse(
    List<NotificationResponse> items,
    long unreadCount
) {
}
