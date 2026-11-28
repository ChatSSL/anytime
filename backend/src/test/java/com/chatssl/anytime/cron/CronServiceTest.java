package com.chatssl.anytime.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Rating;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomChange;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.RoomStatus;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.AttendanceRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.RatingRepository;
import com.chatssl.anytime.repository.RoomChangeRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;

class CronServiceTest {

    /** 2026-11-25(수) 10:40 */
    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final RoomChangeRepository changes = mock(RoomChangeRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final ChatRepository chats = mock(ChatRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final CronService service = new CronService(rooms, roomMembers, changes, ratings, attendance,
        chats, messages, notifications, notificationService, clock, 60);

    private User u1;
    private User u2;
    private User u3;

    @BeforeEach
    void setUp() {
        u1 = user(1L, "데모유저");
        u2 = user(2L, "민트초코");
        u3 = user(3L, "보드왕");
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ 자동 취소·완료

    @Test
    void 시작했는데_2명_미만이면_취소_끝났으면_완료() {
        Room ended = room(10L, u1, TODAY, 540, 600, u1, u2, u3);          // 9:00~10:00, 3명 → 완료
        Room alone = room(11L, u2, TODAY, 630, 720, u2);                 // 10:30 시작, 1명 → 취소
        Room later = room(12L, u1, TODAY, 660, 720, u1);                 // 11:00 시작 → 아직
        Room official = room(13L, u3, TODAY, 600, 720, u3);              // 운영팀, 진행 중 → 그대로
        official.setOfficial(true);
        Room ongoing = room(14L, u1, TODAY, 600, 720, u1, u2);           // 2명, 진행 중 → 그대로
        when(rooms.findDueBy(RoomStatus.OPEN, TODAY)).thenReturn(List.of(ended, alone, later, official, ongoing));
        // 1번은 이미 평가를 마쳤다
        when(attendance.existsByRoomIdAndConfirmerId(10L, 1L)).thenReturn(true);

        CronService.CloseResult res = service.closeStarted();

        assertThat(res.finished()).isEqualTo(1);
        assertThat(res.cancelled()).isEqualTo(1);
        assertThat(ended.getStatus()).isEqualTo(RoomStatus.DONE);
        assertThat(alone.getStatus()).isEqualTo(RoomStatus.CANCELED);
        assertThat(later.getStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(official.getStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(ongoing.getStatus()).isEqualTo(RoomStatus.OPEN);

        verify(notificationService, never()).notify(eq(u1), eq(NotifType.RATE), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(u2), eq(NotifType.RATE), eq("모임은 어땠나요?"), anyString(), eq("/review/10"));
        verify(notificationService).notify(eq(u3), eq(NotifType.RATE), eq("모임은 어땠나요?"), anyString(), eq("/review/10"));
        verify(notificationService).notify(eq(u2), eq(NotifType.CANCEL), eq("모임이 취소됐어요"), anyString(), eq("/rooms/11"));
        verify(messages, times(2)).save(any(Message.class));
    }

    @Test
    void 오래된_모임은_상태만_정리하고_알림은_보내지_않는다() {
        Room oldDone = room(20L, u1, TODAY.minusDays(10), 540, 600, u1, u2);
        Room oldAlone = room(21L, u1, TODAY.minusDays(3), 540, 600, u1);
        when(rooms.findDueBy(RoomStatus.OPEN, TODAY)).thenReturn(List.of(oldDone, oldAlone));

        CronService.CloseResult res = service.closeStarted();

        assertThat(res.finished()).isEqualTo(1);
        assertThat(res.cancelled()).isEqualTo(1);
        assertThat(oldDone.getStatus()).isEqualTo(RoomStatus.DONE);
        assertThat(oldAlone.getStatus()).isEqualTo(RoomStatus.CANCELED);
        verify(notificationService, never()).notify(any(User.class), any(NotifType.class), anyString(), anyString(), anyString());
    }

    @Test
    void 잠근_뒤_이미_처리된_방은_건너뛴다() {
        Room stale = room(30L, u1, TODAY, 540, 600, u1, u2);
        Room locked = room(30L, u1, TODAY, 540, 600, u1, u2);
        locked.setStatus(RoomStatus.CANCELED);
        when(rooms.findDueBy(RoomStatus.OPEN, TODAY)).thenReturn(List.of(stale));
        when(rooms.findByIdForUpdate(30L)).thenReturn(Optional.of(locked));

        CronService.CloseResult res = service.closeStarted();

        assertThat(res.finished()).isZero();
        assertThat(locked.getStatus()).isEqualTo(RoomStatus.CANCELED);
    }

    // ------------------------------------------------------------ 일정 변경 정리

    @Test
    void 마감까지_답이_안_끝난_일정_변경은_무산() {
        Room soon = room(40L, u1, TODAY, 660, 720, u1, u2);            // 11:00 시작 → 10:30 마감 지남
        Room tomorrow = room(41L, u1, TODAY.plusDays(1), 660, 720, u1, u2);
        Room cancelled = room(42L, u1, TODAY.plusDays(1), 660, 720, u1, u2);
        cancelled.setStatus(RoomStatus.CANCELED);
        RoomChange c1 = new RoomChange(soon, 2, 690, 750, null);
        RoomChange c2 = new RoomChange(tomorrow, 3, 690, 750, null);
        RoomChange c3 = new RoomChange(cancelled, 3, 690, 750, null);
        when(changes.findWithRoomByStatus(ChangeStatus.PENDING)).thenReturn(List.of(c1, c2, c3));

        int rejected = service.rejectStaleChanges();

        assertThat(rejected).isEqualTo(2);
        assertThat(c1.getStatus()).isEqualTo(ChangeStatus.REJECTED);
        assertThat(c2.getStatus()).isEqualTo(ChangeStatus.PENDING);
        assertThat(c3.getStatus()).isEqualTo(ChangeStatus.REJECTED);
        verify(notifications).clearPendingAction(NotifType.CHANGE, "/rooms/40");
        verify(notifications).clearPendingAction(NotifType.CHANGE, "/rooms/42");
        // 취소된 방에는 시스템 메시지를 남기지 않는다
        verify(messages, times(1)).save(any(Message.class));
        // 일정은 그대로
        assertThat(soon.getStartMin()).isEqualTo(660);
    }

    // ------------------------------------------------------------ 리마인드

    @Test
    void 시작_1시간_안의_모임만_한_사람에_한_번_리마인드() {
        Room soon = room(50L, u1, TODAY, 690, 750, u1, u2, u3);          // 11:30 시작 → 50분 전
        Room far = room(51L, u1, TODAY, 720, 780, u1, u2);               // 12:00 → 80분 전
        Room alone = room(52L, u1, TODAY, 700, 760, u1);                 // 1명 → 자동 취소 예정
        Room started = room(53L, u1, TODAY, 600, 700, u1, u2);           // 이미 시작
        when(rooms.findUpcoming(RoomStatus.OPEN, TODAY)).thenReturn(List.of(soon, far, alone, started));
        when(notifications.existsByUserIdAndTypeAndLink(3L, NotifType.REMINDER, "/rooms/50")).thenReturn(true);

        int sent = service.remind();

        assertThat(sent).isEqualTo(2);
        verify(notificationService).notify(eq(u1), eq(NotifType.REMINDER), eq("곧 모임이 시작돼요"),
            eq("'보드게임 한 판' 모임이 11:30에 시작해요 · 학생회관"), eq("/rooms/50"));
        verify(notificationService).notify(eq(u2), eq(NotifType.REMINDER), anyString(), anyString(), eq("/rooms/50"));
        verify(notificationService, never()).notify(eq(u3), any(NotifType.class), anyString(), anyString(), anyString());
        verify(notificationService, never()).notify(any(User.class), any(NotifType.class), anyString(), anyString(), eq("/rooms/51"));
        verify(notificationService, never()).notify(any(User.class), any(NotifType.class), anyString(), anyString(), eq("/rooms/52"));
        verify(notificationService, never()).notify(any(User.class), any(NotifType.class), anyString(), anyString(), eq("/rooms/53"));
    }

    // ------------------------------------------------------------ 평가 반영

    @Test
    void 평가를_모아서_매너_점수에_반영하고_applied_at을_채운다() {
        Room r = room(60L, u1, TODAY, 540, 600, u1, u2, u3);
        Rating a = new Rating(r, u1, u2, 5, null, null);
        Rating b = new Rating(r, u3, u2, 1, "기타", null);
        Rating c = new Rating(r, u2, u3, 4, null, null);
        when(ratings.findReadyToApply(NOW.minusMinutes(60))).thenReturn(List.of(a, b, c));

        int applied = service.applyRatings();

        assertThat(applied).isEqualTo(3);
        // 36.8 + 0.2 - 0.5 = 36.5
        assertThat(u2.getManner()).isEqualByComparingTo("36.5");
        // 36.8 + 0.1 = 36.9
        assertThat(u3.getManner()).isEqualByComparingTo("36.9");
        assertThat(u1.getManner()).isEqualByComparingTo("36.8");
        assertThat(List.of(a, b, c)).allSatisfy(x -> assertThat(x.getAppliedAt()).isEqualTo(NOW));
    }

    @Test
    void 매너_점수는_30도에서_45도_사이() {
        assertThat(CronService.clamp(new BigDecimal("45.1"))).isEqualByComparingTo("45.0");
        assertThat(CronService.clamp(new BigDecimal("29.5"))).isEqualByComparingTo("30.0");
        assertThat(CronService.clamp(new BigDecimal("36.5"))).isEqualByComparingTo("36.5");
    }

    // ------------------------------------------------------------ 도우미

    private Room room(Long id, User host, LocalDate date, int start, int end, User... members) {
        Room r = new Room(host, "보드게임 한 판", "보드게임", date.getDayOfWeek().getValue() - 1,
            start, end, date, "학생회관", 4, null);
        r.setId(id);
        when(rooms.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        when(roomMembers.findActiveWithUserByRoomId(id))
            .thenReturn(Arrays.stream(members).map(u -> new RoomMember(r, u)).toList());
        Chat chat = Chat.forRoom(r);
        chat.setId(id + 1000);
        when(chats.findByRoomId(id)).thenReturn(Optional.of(chat));
        return r;
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }
}
