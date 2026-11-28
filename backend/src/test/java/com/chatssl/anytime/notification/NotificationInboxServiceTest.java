package com.chatssl.anytime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.NotifResolution;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.dto.NotificationListResponse;
import com.chatssl.anytime.notification.dto.NotificationResponse;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.NotificationRepository;

class NotificationInboxServiceTest {

    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final NotificationInboxService service = new NotificationInboxService(notifications, blocks, clock);

    private final User me = user(1L, "데모유저");
    private final User friend = user(2L, "민트초코");
    private final User blocked = user(3L, "차단유저");

    @Test
    void 목록은_소문자_타입으로_주고_차단_관계_사람의_알림은_뺀다() {
        Notification fromFriend = notif(10L, NotifType.REQUEST, friend, null);
        fromFriend.setNeedsAction(true);
        Notification fromBlocked = notif(11L, NotifType.REQUEST, blocked, null);
        Notification system = notif(12L, NotifType.REMINDER, null, NOW.minusMinutes(5));
        system.setResolved(NotifResolution.ACCEPTED);
        when(notifications.findTop100ByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(fromFriend, fromBlocked, system));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));

        NotificationListResponse res = service.list(1L);

        assertThat(res.items()).extracting(NotificationResponse::id).containsExactly(10L, 12L);
        assertThat(res.items().get(0).type()).isEqualTo("request");
        assertThat(res.items().get(0).relatedUserId()).isEqualTo(2L);
        assertThat(res.items().get(0).needsAction()).isTrue();
        assertThat(res.items().get(1).resolved()).isEqualTo("accepted");
        assertThat(res.items().get(1).read()).isTrue();
        assertThat(res.unreadCount()).isEqualTo(1);
    }

    @Test
    void 하나_읽으면_readAt을_지금으로_채운다() {
        Notification n = notif(10L, NotifType.JOIN, null, null);
        when(notifications.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(n));

        NotificationResponse res = service.read(1L, 10L);

        assertThat(n.getReadAt()).isEqualTo(NOW);
        assertThat(res.read()).isTrue();
    }

    @Test
    void 남의_알림은_404() {
        when(notifications.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(1L, 10L))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void 모두_읽음은_바뀐_개수를_돌려준다() {
        when(notifications.markAllRead(1L, NOW)).thenReturn(3);

        assertThat(service.readAll(1L)).isEqualTo(3);
    }

    private Notification notif(Long id, NotifType type, User related, LocalDateTime readAt) {
        Notification n = new Notification(me, type, "제목", "본문", "/rooms/1");
        n.setId(id);
        n.setRelatedUser(related);
        n.setReadAt(readAt);
        n.setCreatedAt(NOW.minusMinutes(id));
        return n;
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }
}
