package com.chatssl.anytime.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.notification.dto.NotificationListResponse;
import com.chatssl.anytime.notification.dto.NotificationResponse;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 17단계: 알림 센터 (목록·읽음·모두 읽음).
 * 알림을 '만드는' 쪽은 3번의 NotificationService.notify(...)이고, 여기는 '보는' 쪽만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    /** 최근 100개. 차단 관계인 사람이 관련된 알림(1:1 요청 등)은 숨긴다 */
    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId) {
        Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
        List<NotificationResponse> items = notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
            .filter(n -> n.getRelatedUser() == null || !hidden.contains(n.getRelatedUser().getId()))
            .map(NotificationResponse::of)
            .toList();
        long unread = items.stream().filter(n -> !n.read()).count();
        return new NotificationListResponse(items, unread);
    }

    /** 하나 읽음. 이미 읽었으면 그대로. 남의 알림이면 404 */
    @Transactional
    public NotificationResponse read(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now(clock));
        }
        return NotificationResponse.of(n);
    }

    /** 모두 읽음. 바뀐 개수를 돌려준다 */
    @Transactional
    public int readAll(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now(clock));
    }
}
