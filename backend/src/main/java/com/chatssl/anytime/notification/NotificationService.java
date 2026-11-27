package com.chatssl.anytime.notification;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 공통 알림 발송 서비스 (12단계~).
 * 사용자 설정(User.settings.notif.<type>)을 확인한 뒤 알림을 저장한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int TITLE_MAX = 50;
    private static final int BODY_MAX = 200;
    private static final int LINK_MAX = 255;

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification notify(User user, NotifType type, String title, String body, String link) {
        return notify(user, type, title, body, link, null, false);
    }

    @Transactional
    public Notification notify(
        User user,
        NotifType type,
        String title,
        String body,
        String link,
        User relatedUser,
        boolean needsAction
    ) {
        if (user == null || type == null || !isEnabled(user, type)) {
            return null;
        }
        Notification notification = new Notification(
            user,
            type,
            truncate(title, TITLE_MAX),
            truncate(body, BODY_MAX),
            truncateNullable(link, LINK_MAX)
        );
        notification.setRelatedUser(relatedUser);
        notification.setNeedsAction(needsAction);
        return notificationRepository.save(notification);
    }

    /**
     * settings.notif.<lowercase_type> 확인.
     * settings가 null이거나 notif 맵 또는 해당 키가 없으면 기본값 true로 발송한다.
     */
    public boolean isEnabled(User user, NotifType type) {
        Map<String, Object> settings = user.getSettings();
        if (settings == null) {
            return true;
        }
        Object notifObj = settings.get("notif");
        if (!(notifObj instanceof Map<?, ?> notifMap)) {
            return true;
        }
        String key = type.name().toLowerCase(Locale.ROOT);
        Object value = notifMap.get(key);
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return true;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private static String truncateNullable(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
