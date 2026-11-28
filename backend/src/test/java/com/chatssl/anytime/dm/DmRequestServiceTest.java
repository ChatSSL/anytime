package com.chatssl.anytime.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.dm.dto.CreateDmRequest;
import com.chatssl.anytime.dm.dto.DmRequestListResponse;
import com.chatssl.anytime.dm.dto.DmRequestResponse;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.DmRequest;
import com.chatssl.anytime.domain.DmStatus;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.NotifResolution;
import com.chatssl.anytime.domain.NotifType;
import com.chatssl.anytime.domain.Notification;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.notification.NotificationService;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.DmRequestRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.NotificationRepository;
import com.chatssl.anytime.repository.UserRepository;

class DmRequestServiceTest {

    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 25).atTime(10, 40);

    private final DmRequestRepository dmRequests = mock(DmRequestRepository.class);
    private final ChatRepository chats = mock(ChatRepository.class);
    private final ChatMemberRepository chatMembers = mock(ChatMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService notificationService = new NotificationService(notifications);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final DmRequestService service = new DmRequestService(
        dmRequests, chats, chatMembers, messages, notifications, notificationService, blocks, users, clock);

    private User me;
    private User other;
    private User blocked;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저");
        other = user(2L, "민트초코");
        blocked = user(3L, "차단유저");

        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(users.findById(2L)).thenReturn(Optional.of(other));
        when(users.findById(3L)).thenReturn(Optional.of(blocked));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));
        when(blocks.findHiddenUserIds(2L)).thenReturn(Set.of());

        when(dmRequests.save(any(DmRequest.class))).thenAnswer(inv -> {
            DmRequest d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(50L);
            }
            return d;
        });
        when(chats.save(any(Chat.class))).thenAnswer(inv -> {
            Chat c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(300L);
            }
            return c;
        });
        when(chatMembers.save(any(ChatMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 일대일_요청_전송_시_저장하고_상대방에게_REQUEST_알림을_보낸다() {
        when(chatMembers.findDmChatIdsBetween(1L, 2L)).thenReturn(List.of());
        when(dmRequests.existsPendingBetween(1L, 2L)).thenReturn(false);

        CreateDmRequest req = new CreateDmRequest(2L, 2, 720, 840, " 보드게임 ", " 같이 한 판 해요 ");
        DmRequestResponse res = service.create(1L, req);

        assertThat(res.id()).isEqualTo(50L);
        assertThat(res.status()).isEqualTo("pending");
        assertThat(res.activity()).isEqualTo("보드게임");
        assertThat(res.message()).isEqualTo("같이 한 판 해요");
        assertThat(res.toUser().id()).isEqualTo(2L);

        ArgumentCaptor<Notification> notifCap = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notifCap.capture());
        assertThat(notifCap.getValue().getUser().getId()).isEqualTo(2L);
        assertThat(notifCap.getValue().getType()).isEqualTo(NotifType.REQUEST);
        assertThat(notifCap.getValue().getRelatedUser().getId()).isEqualTo(1L);
        assertThat(notifCap.getValue().isNeedsAction()).isTrue();
    }

    @Test
    void 본인_차단_정지_중복요청_기존DM_조건을_모두_거절한다() {
        // 본인 요청 -> 400 DM_REQUEST_SELF
        assertCode(() -> service.create(1L, new CreateDmRequest(1L, 2, 720, 840, "산책", null)),
            ErrorCode.DM_REQUEST_SELF);

        // 호출자 정지 -> 403 USER_SUSPENDED
        me.setSanction(2);
        assertCode(() -> service.create(1L, new CreateDmRequest(2L, 2, 720, 840, "산책", null)),
            ErrorCode.USER_SUSPENDED);
        me.setSanction(0);

        // 차단 사용자 -> 404 USER_NOT_FOUND
        assertCode(() -> service.create(1L, new CreateDmRequest(3L, 2, 720, 840, "산책", null)),
            ErrorCode.USER_NOT_FOUND);

        // 대상 정지 -> 404 USER_NOT_FOUND
        other.setSanction(2);
        assertCode(() -> service.create(1L, new CreateDmRequest(2L, 2, 720, 840, "산책", null)),
            ErrorCode.USER_NOT_FOUND);
        other.setSanction(0);

        // 기존 DM 존재 -> 409 DM_CHAT_ALREADY_EXISTS
        when(chatMembers.findDmChatIdsBetween(1L, 2L)).thenReturn(List.of(300L));
        assertCode(() -> service.create(1L, new CreateDmRequest(2L, 2, 720, 840, "산책", null)),
            ErrorCode.DM_CHAT_ALREADY_EXISTS);
        when(chatMembers.findDmChatIdsBetween(1L, 2L)).thenReturn(List.of());

        // 양방향 PENDING 요청 존재 -> 409 DM_REQUEST_ALREADY_EXISTS
        when(dmRequests.existsPendingBetween(1L, 2L)).thenReturn(true);
        assertCode(() -> service.create(1L, new CreateDmRequest(2L, 2, 720, 840, "산책", null)),
            ErrorCode.DM_REQUEST_ALREADY_EXISTS);
    }

    @Test
    void 목록_조회는_차단_사용자를_제외한_received와_sent를_함께_반환한다() {
        DmRequest recOk = new DmRequest(other, me, 2, 720, 840, "보드게임", "같이 해요");
        recOk.setId(1L);
        DmRequest recBlocked = new DmRequest(blocked, me, 2, 720, 840, "산책", null);
        recBlocked.setId(2L);
        DmRequest sentOk = new DmRequest(me, other, 4, 720, 810, "카페공부", null);
        sentOk.setId(3L);

        when(dmRequests.findReceivedWithUsers(1L, DmStatus.PENDING)).thenReturn(List.of(recOk, recBlocked));
        when(dmRequests.findSentWithUsers(1L)).thenReturn(List.of(sentOk));

        DmRequestListResponse res = service.list(1L);

        assertThat(res.received()).extracting(DmRequestResponse::id).containsExactly(1L);
        assertThat(res.sent()).extracting(DmRequestResponse::id).containsExactly(3L);
    }

    @Test
    void 요청_수락_시_DM_채팅방을_생성하고_시스템메시지_요청메시지_알림해결_수락알림을_처리한다() {
        DmRequest pending = new DmRequest(other, me, 2, 720, 840, "보드게임", "수요일에 같이 해요");
        pending.setId(10L);
        Notification reqNotif = new Notification(me, NotifType.REQUEST, "1:1 요청이 왔어요", "본문", null);
        reqNotif.setRelatedUser(other);
        reqNotif.setNeedsAction(true);

        when(dmRequests.findByIdForUpdate(10L)).thenReturn(Optional.of(pending));
        when(chatMembers.findDmChatIdsBetween(2L, 1L)).thenReturn(List.of());
        when(notifications.findPendingActionNotifications(1L, 2L, NotifType.REQUEST)).thenReturn(List.of(reqNotif));

        DmRequestResponse res = service.accept(1L, 10L);

        assertThat(res.status()).isEqualTo("accepted");
        assertThat(res.chatId()).isEqualTo(300L);
        verify(chats).save(any(Chat.class));
        verify(chatMembers, times(2)).save(any(ChatMember.class));

        ArgumentCaptor<Message> msgCap = ArgumentCaptor.forClass(Message.class);
        verify(messages, times(2)).save(msgCap.capture());
        assertThat(msgCap.getAllValues().get(0).isSystem()).isTrue();
        assertThat(msgCap.getAllValues().get(0).getText()).isEqualTo("서로 공강이 겹치는 수 12:00–14:00에 만나요");
        assertThat(msgCap.getAllValues().get(1).getSender().getId()).isEqualTo(2L);
        assertThat(msgCap.getAllValues().get(1).getText()).isEqualTo("수요일에 같이 해요");

        assertThat(reqNotif.getResolved()).isEqualTo(NotifResolution.ACCEPTED);
        assertThat(reqNotif.isNeedsAction()).isFalse();
        assertThat(reqNotif.getReadAt()).isEqualTo(NOW);

        ArgumentCaptor<Notification> notifCap = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(notifCap.capture());
        Notification joinNotif = notifCap.getAllValues().get(1);
        assertThat(joinNotif.getUser().getId()).isEqualTo(2L);
        assertThat(joinNotif.getType()).isEqualTo(NotifType.JOIN);
        assertThat(joinNotif.getLink()).isEqualTo("/chat/300");
    }

    @Test
    void 이미_DM_채팅방이_있으면_수락_시_기존_채팅방을_재사용하고_메시지를_추가한다() {
        Chat existing = Chat.dm();
        existing.setId(777L);
        DmRequest pending = new DmRequest(other, me, 4, 720, 780, "산책", "다시 만나요");
        pending.setId(11L);

        when(dmRequests.findByIdForUpdate(11L)).thenReturn(Optional.of(pending));
        when(chatMembers.findDmChatIdsBetween(2L, 1L)).thenReturn(List.of(777L));
        when(chats.findById(777L)).thenReturn(Optional.of(existing));
        when(notifications.findPendingActionNotifications(1L, 2L, NotifType.REQUEST)).thenReturn(List.of());

        DmRequestResponse res = service.accept(1L, 11L);

        assertThat(res.chatId()).isEqualTo(777L);
        verify(chats, never()).save(any(Chat.class));
        verify(messages, times(2)).save(any(Message.class));
    }

    @Test
    void 요청_거절_시_권한과_상태를_검증하고_DECLINED로_변경하며_알림을_해결한다() {
        DmRequest pending = new DmRequest(other, me, 2, 720, 840, "보드게임", null);
        pending.setId(12L);
        Notification reqNotif = new Notification(me, NotifType.REQUEST, "1:1 요청이 왔어요", "본문", null);
        reqNotif.setRelatedUser(other);
        reqNotif.setNeedsAction(true);

        when(dmRequests.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));
        when(notifications.findPendingActionNotifications(1L, 2L, NotifType.REQUEST)).thenReturn(List.of(reqNotif));

        // 수신자가 아닌 사용자가 거절 시도 -> 403 FORBIDDEN
        assertCode(() -> service.decline(2L, 12L), ErrorCode.FORBIDDEN);

        DmRequestResponse res = service.decline(1L, 12L);
        assertThat(res.status()).isEqualTo("declined");
        assertThat(reqNotif.getResolved()).isEqualTo(NotifResolution.DECLINED);
        assertThat(reqNotif.isNeedsAction()).isFalse();

        // 이미 처리된 요청 재거절 -> 409 DM_REQUEST_ALREADY_PROCESSED
        assertCode(() -> service.decline(1L, 12L), ErrorCode.DM_REQUEST_ALREADY_PROCESSED);
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
