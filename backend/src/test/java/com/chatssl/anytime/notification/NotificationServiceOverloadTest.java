package com.chatssl.anytime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.UserRepository;

class NotificationServiceOverloadTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService service = new NotificationService(notifications, users);

    private User target;

    @BeforeEach
    void setUp() {
        target = new User("hash-1", "SEED:u1@dankook.ac.kr", "단국대학교", "데모유저");
        target.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(target));
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });
    }

    @Test
    void userId_4개_인자_오버로드는_기본_제목을_채워_저장한다() {
        Notification saved = service.notify(1L, NotifType.RATE, "모임은 어땠나요?", "/review/10");

        assertThat(saved).isNotNull();
        assertThat(saved.getUser()).isSameAs(target);
        assertThat(saved.getType()).isEqualTo(NotifType.RATE);
        assertThat(saved.getTitle()).isEqualTo("평가 요청");
        assertThat(saved.getBody()).isEqualTo("모임은 어땠나요?");
        assertThat(saved.getLink()).isEqualTo("/review/10");
        assertThat(saved.getRelatedUser()).isNull();
        assertThat(saved.isNeedsAction()).isFalse();
    }

    @Test
    void userId_4개_인자_오버로드는_사용자_설정이_꺼져있거나_사용자가_없으면_저장하지_않는다() {
        when(users.findById(999L)).thenReturn(Optional.empty());
        assertThat(service.notify(999L, NotifType.REMINDER, "1시간 뒤 시작해요", "/rooms/1")).isNull();

        target.setSettings(Map.of("notif", Map.of("reminder", false)));
        assertThat(service.notify(1L, NotifType.REMINDER, "1시간 뒤 시작해요", "/rooms/1")).isNull();

        verify(notifications, never()).save(any());
    }

    @Test
    void User_4개_인자_오버로드도_모든_알림_타입에_대해_기본_제목을_채운다() {
        for (NotifType type : NotifType.values()) {
            Notification saved = service.notify(target, type, "안내 본문", "/home");
            assertThat(saved).isNotNull();
            assertThat(saved.getTitle()).isNotBlank();
            assertThat(saved.getType()).isEqualTo(type);
        }
    }
}
