package com.chatssl.anytime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.NotificationRepository;

class NotificationServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(notifications);

    private User target;
    private User actor;

    @BeforeEach
    void setUp() {
        target = new User("hash-1", "SEED:u1@dankook.ac.kr", "단국대학교", "데모유저");
        target.setId(1L);
        actor = new User("hash-2", "SEED:u2@dankook.ac.kr", "단국대학교", "민트초코");
        actor.setId(2L);

        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(10L);
            return n;
        });
    }

    @Test
    void settings가_null이거나_notif키가_없으면_기본값_true로_알림을_저장한다() {
        target.setSettings(null);
        Notification saved1 = service.notify(target, NotifType.JOIN, "새 참여자", "합류했어요", "/rooms/1", actor, false);
        assertThat(saved1).isNotNull();

        target.setSettings(Map.of("notif", Map.of("reco", true)));
        Notification saved2 = service.notify(target, NotifType.CANCEL, "모임 취소", "취소됐어요", "/rooms/1");
        assertThat(saved2).isNotNull();

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues().get(0).getRelatedUser()).isEqualTo(actor);
        assertThat(cap.getAllValues().get(0).isNeedsAction()).isFalse();
        assertThat(cap.getAllValues().get(1).getType()).isEqualTo(NotifType.CANCEL);
    }

    @Test
    void settings에서_해당_알림_종류를_끄면_저장하지_않는다() {
        target.setSettings(Map.of("notif", Map.of("join", false, "reco", true)));

        Notification result = service.notify(target, NotifType.JOIN, "새 참여자", "합류했어요", "/rooms/1");

        assertThat(result).isNull();
        verify(notifications, never()).save(any());
    }

    @Test
    void needsAction과_길이제한_초과_텍스트도_안전하게_저장한다() {
        target.setSettings(Map.of("ads", true));
        String longTitle = "가".repeat(70);
        String longBody = "나".repeat(250);

        Notification saved = service.notify(target, NotifType.REQUEST, longTitle, longBody, "/dm", actor, true);

        assertThat(saved).isNotNull();
        assertThat(saved.getTitle()).hasSize(50);
        assertThat(saved.getBody()).hasSize(200);
        assertThat(saved.isNeedsAction()).isTrue();
        assertThat(saved.getRelatedUser()).isEqualTo(actor);
    }
}
