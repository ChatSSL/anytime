package com.chatssl.anytime.roomchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.CancelLog;
import com.chatssl.anytime.domain.ChangeStatus;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifResolution;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomChange;
import com.chatssl.anytime.domain.RoomChangeVote;
import com.chatssl.anytime.domain.RoomChangeVoteId;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.CancelLogRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.RoomChangeRepository;
import com.chatssl.anytime.repository.RoomChangeVoteRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.RoomRepository;
import com.chatssl.anytime.roomchange.dto.ChangeResponse;
import com.chatssl.anytime.roomchange.dto.ProposeChangeRequest;

class RoomChangeServiceTest {

    /** 2026-11-25(수) 10:40 */
    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);
    /** 금요일 */
    private static final LocalDate FRI = LocalDate.of(2026, 11, 27);

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final RoomChangeRepository changes = mock(RoomChangeRepository.class);
    private final RoomChangeVoteRepository votes = mock(RoomChangeVoteRepository.class);
    private final ChatRepository chats = mock(ChatRepository.class);
    private final ChatMemberRepository chatMembers = mock(ChatMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final CancelLogRepository cancelLogs = mock(CancelLogRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final RoomChangeService service = new RoomChangeService(rooms, roomMembers, changes, votes,
        chats, chatMembers, messages, cancelLogs, notifications, notificationService, clock);

    private User host;
    private User u2;
    private User u3;
    private Room room;
    private Chat chat;
    private List<RoomMember> members;

    @BeforeEach
    void setUp() {
        host = user(1L, "데모유저");
        u2 = user(2L, "민트초코");
        u3 = user(3L, "보드왕");
        room = new Room(host, "보드게임 한 판", "보드게임", 4, 720, 780, FRI, "학생회관", 4, null);
        room.setId(10L);
        chat = Chat.forRoom(room);
        chat.setId(55L);
        members = new ArrayList<>(List.of(new RoomMember(room, host), new RoomMember(room, u2), new RoomMember(room, u3)));

        when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(rooms.findWithHostById(10L)).thenReturn(Optional.of(room));
        when(roomMembers.findActiveWithUserByRoomId(10L)).thenAnswer(inv -> members);
        when(chats.findByRoomId(10L)).thenReturn(Optional.of(chat));
        when(changes.findByRoomIdAndStatus(10L, ChangeStatus.PENDING)).thenReturn(List.of());
        when(changes.save(any(RoomChange.class))).thenAnswer(inv -> {
            RoomChange c = inv.getArgument(0);
            c.setId(70L);
            return c;
        });
        when(votes.save(any(RoomChangeVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ 제안

    @Test
    void 방장이_제안하면_대기로_저장하고_다른_참여자에게_답변_필요_알림() {
        ChangeResponse res = service.propose(1L, 10L, new ProposeChangeRequest(4, 750, 810, null));

        ArgumentCaptor<RoomChange> saved = ArgumentCaptor.forClass(RoomChange.class);
        verify(changes).save(saved.capture());
        assertThat(saved.getValue().getNewStart()).isEqualTo(750);
        assertThat(saved.getValue().getNewPlace()).isNull();
        assertThat(saved.getValue().getStatus()).isEqualTo(ChangeStatus.PENDING);

        verify(notificationService).notify(eq(u2), eq(NotifType.CHANGE), eq("일정이 바뀌었어요"), anyString(),
            eq("/rooms/10"), eq(host), eq(true));
        verify(notificationService).notify(eq(u3), eq(NotifType.CHANGE), eq("일정이 바뀌었어요"), anyString(),
            eq("/rooms/10"), eq(host), eq(true));
        verify(notificationService, never()).notify(eq(host), any(NotifType.class), anyString(), anyString(),
            anyString(), any(), eq(true));
        verify(messages).save(any(Message.class));

        assertThat(res.status()).isEqualTo(ChangeStatus.PENDING);
        assertThat(res.required()).isEqualTo(2);
        assertThat(res.accepted()).isZero();
        assertThat(res.isHost()).isTrue();
        assertThat(res.canVote()).isFalse();
        // 아직 확정 전이라 모임 일정은 그대로
        assertThat(room.getStartMin()).isEqualTo(720);
    }

    @Test
    void 다른_참여자가_없으면_바로_확정() {
        members = new ArrayList<>(List.of(new RoomMember(room, host)));

        ChangeResponse res = service.propose(1L, 10L, new ProposeChangeRequest(0, 600, 660, "도서관 앞 카페"));

        assertThat(res.status()).isEqualTo(ChangeStatus.APPLIED);
        assertThat(room.getDay()).isEqualTo(0);
        assertThat(room.getStartMin()).isEqualTo(600);
        assertThat(room.getEndMin()).isEqualTo(660);
        assertThat(room.getPlace()).isEqualTo("도서관 앞 카페");
        // 수요일 기준 다음 월요일
        assertThat(room.getMeetDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void 제안_검사() {
        assertCode(() -> service.propose(2L, 10L, new ProposeChangeRequest(4, 750, 810, null)), ErrorCode.CHANGE_HOST_ONLY);
        assertCode(() -> service.propose(9L, 10L, new ProposeChangeRequest(4, 750, 810, null)), ErrorCode.ROOM_NOT_MEMBER);
        assertCode(() -> service.propose(1L, 10L, new ProposeChangeRequest(4, 720, 780, " 학생회관 ")), ErrorCode.CHANGE_SAME);
        assertCode(() -> service.propose(1L, 10L, new ProposeChangeRequest(5, 720, 780, null)), ErrorCode.INVALID_INPUT);
        assertCode(() -> service.propose(1L, 10L, new ProposeChangeRequest(4, 800, 780, null)), ErrorCode.INVALID_INPUT);

        when(changes.findByRoomIdAndStatus(10L, ChangeStatus.PENDING))
            .thenReturn(List.of(new RoomChange(room, 4, 750, 810, null)));
        assertCode(() -> service.propose(1L, 10L, new ProposeChangeRequest(4, 750, 810, null)), ErrorCode.CHANGE_ALREADY_PENDING);

        // 오늘 11:00 시작 → 10:40은 30분 전 마감 이후
        room.setMeetDate(NOW.toLocalDate());
        room.setDay(2);
        room.setStartMin(660);
        room.setEndMin(720);
        assertCode(() -> service.propose(1L, 10L, new ProposeChangeRequest(2, 700, 760, null)), ErrorCode.ROOM_CLOSED);

        verify(changes, never()).save(any(RoomChange.class));
    }

    // ------------------------------------------------------------ 투표

    @Test
    void 한_명만_동의하면_아직_대기이고_내_알림은_처리됨() {
        RoomChange change = pendingChange();
        Notification n = new Notification(u2, NotifType.CHANGE, "일정이 바뀌었어요", "본문", "/rooms/10");
        n.setNeedsAction(true);
        when(notifications.findByUserIdAndTypeAndLinkAndNeedsActionTrueAndResolvedIsNull(2L, NotifType.CHANGE, "/rooms/10"))
            .thenReturn(List.of(n));

        ChangeResponse res = service.vote(2L, change.getId(), true);

        verify(votes).save(any(RoomChangeVote.class));
        assertThat(res.status()).isEqualTo(ChangeStatus.PENDING);
        assertThat(res.accepted()).isEqualTo(1);
        assertThat(res.required()).isEqualTo(2);
        assertThat(res.myVote()).isTrue();
        assertThat(res.canVote()).isFalse();
        assertThat(n.getResolved()).isEqualTo(NotifResolution.ACCEPTED);
        assertThat(n.getReadAt()).isEqualTo(NOW);
        assertThat(room.getStartMin()).isEqualTo(720);
    }

    @Test
    void 마지막_사람이_동의하면_일정을_확정하고_모두에게_알린다() {
        RoomChange change = pendingChange();
        when(votes.findByChangeId(70L)).thenReturn(List.of(new RoomChangeVote(change, u2, true)));

        ChangeResponse res = service.vote(3L, 70L, true);

        assertThat(res.status()).isEqualTo(ChangeStatus.APPLIED);
        assertThat(change.getStatus()).isEqualTo(ChangeStatus.APPLIED);
        assertThat(room.getStartMin()).isEqualTo(750);
        assertThat(room.getEndMin()).isEqualTo(810);
        assertThat(room.getMeetDate()).isEqualTo(FRI);
        assertThat(room.getPlace()).isEqualTo("학생회관");
        verify(notificationService, times(3)).notify(any(User.class), eq(NotifType.CHANGE), eq("일정이 확정됐어요"),
            anyString(), eq("/rooms/10"));
    }

    @Test
    void 빠지면_매너_차감_없이_나가고_남은_사람이_다_답했으면_확정() {
        RoomChange change = pendingChange();
        when(votes.findByChangeId(70L)).thenReturn(List.of(new RoomChangeVote(change, u2, true)));
        ChatMember cm = new ChatMember(chat, u3);
        when(chatMembers.findById(new ChatMemberId(55L, 3L))).thenReturn(Optional.of(cm));
        RoomMember u3Member = members.get(2);

        ChangeResponse res = service.vote(3L, 70L, false);

        assertThat(u3Member.getLeftAt()).isEqualTo(NOW);
        assertThat(u3Member.getLeaveReason()).isEqualTo(RoomChangeService.DECLINE_REASON);
        ArgumentCaptor<CancelLog> log = ArgumentCaptor.forClass(CancelLog.class);
        verify(cancelLogs).save(log.capture());
        assertThat(log.getValue().getPenalty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(u3.getManner()).isEqualByComparingTo("36.8");
        verify(chatMembers).delete(cm);

        assertThat(res.status()).isEqualTo(ChangeStatus.APPLIED);
        assertThat(res.myVote()).isFalse();
        assertThat(room.getStartMin()).isEqualTo(750);
        // 확정 알림은 남은 두 사람(방장, u2)에게만
        verify(notificationService, times(2)).notify(any(User.class), eq(NotifType.CHANGE), eq("일정이 확정됐어요"),
            anyString(), eq("/rooms/10"));
    }

    @Test
    void 제안_뒤에_들어온_사람은_답하지_않아도_된다() {
        RoomChange change = pendingChange();
        change.setCreatedAt(NOW.minusMinutes(10));
        members.get(1).setJoinedAt(NOW.minusHours(1));
        members.get(2).setJoinedAt(NOW.minusMinutes(5)); // u3은 제안 뒤에 합류

        ChangeResponse res = service.vote(2L, 70L, true);

        assertThat(res.required()).isEqualTo(1);
        assertThat(res.status()).isEqualTo(ChangeStatus.APPLIED);
    }

    @Test
    void 투표_검사() {
        RoomChange change = pendingChange();

        assertCode(() -> service.vote(1L, 70L, true), ErrorCode.CHANGE_HOST_NO_VOTE);
        assertCode(() -> service.vote(9L, 70L, true), ErrorCode.ROOM_NOT_MEMBER);
        assertCode(() -> service.vote(2L, 404L, true), ErrorCode.CHANGE_NOT_FOUND);

        when(votes.existsById(new RoomChangeVoteId(70L, 2L))).thenReturn(true);
        assertCode(() -> service.vote(2L, 70L, true), ErrorCode.CHANGE_ALREADY_VOTED);

        change.setStatus(ChangeStatus.APPLIED);
        assertCode(() -> service.vote(3L, 70L, true), ErrorCode.CHANGE_ALREADY_DECIDED);

        verify(votes, never()).save(any(RoomChangeVote.class));
    }

    @Test
    void 대기_중인_변경_조회() {
        assertThat(service.pending(2L, 10L)).isEmpty();

        RoomChange change = pendingChange();
        when(changes.findByRoomIdAndStatus(10L, ChangeStatus.PENDING)).thenReturn(List.of(change));

        ChangeResponse res = service.pending(2L, 10L).orElseThrow();
        assertThat(res.id()).isEqualTo(70L);
        assertThat(res.currentStart()).isEqualTo(720);
        assertThat(res.newStart()).isEqualTo(750);
        assertThat(res.canVote()).isTrue();
        assertThat(res.myVote()).isNull();

        assertCode(() -> service.pending(9L, 10L), ErrorCode.ROOM_NOT_MEMBER);
    }

    // ------------------------------------------------------------ 도우미

    private RoomChange pendingChange() {
        RoomChange change = new RoomChange(room, 4, 750, 810, null);
        change.setId(70L);
        when(changes.findById(70L)).thenReturn(Optional.of(change));
        return change;
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(code);
    }
}
