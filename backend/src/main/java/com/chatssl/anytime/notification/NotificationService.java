package com.chatssl.anytime.notification;

import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.UserRepository;

/**
 * 공통 알림 발송 서비스 (12단계~).
 * 사용자 설정(User.settings.notif.<type>)을 확인한 뒤 알림을 저장한다.
 */
@Service
public class NotificationService {

    private static final int TITLE_MAX = 50;
    private static final int BODY_MAX = 200;
    private static final int LINK_MAX = 255;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /** 기존 1-arg 테스트 호환용 생성자 */
    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, null);
    }

    /** 단순 알림용 4개 인자 오버로드 (명세 기준: 평가·신고·크론 등). 타입별 기본 제목을 채워 위임한다. */
    @Transactional
    public Notification notify(Long userId, NotifType type, String body, String link) {
        if (userId == null || type == null || userRepository == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        return notify(user, type, defaultTitle(type), body, link, null, false);
    }

    /** 단순 알림용 4개 인자 오버로드 (User 엔티티 보유 시). 타입별 기본 제목을 채워 위임한다. */
    @Transactional
    public Notification notify(User user, NotifType type, String body, String link) {
        if (type == null) {
            return null;
        }
        return notify(user, type, defaultTitle(type), body, link, null, false);
    }

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

    private static String defaultTitle(NotifType type) {
        return switch (type) {
            case RECO -> "새 추천";
            case JOIN -> "모임 소식";
            case REQUEST -> "1:1 요청이 왔어요";
            case REMINDER -> "모임 리마인드";
            case CHANGE -> "일정이 바뀌었어요";
            case CANCEL -> "모임이 취소됐어요";
            case RATE -> "평가 요청";
        };
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
