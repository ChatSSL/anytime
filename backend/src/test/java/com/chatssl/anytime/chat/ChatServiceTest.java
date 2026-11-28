package com.chatssl.anytime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chatssl.anytime.chat.dto.ChatMessagesPageResponse;
import com.chatssl.anytime.chat.dto.ChatSummaryResponse;
import com.chatssl.anytime.chat.dto.UpdateChatLinkRequest;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomMember;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.room.RoomPhase;

class ChatServiceTest {

    private static final LocalDate WED = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime NOW = WED.atTime(10, 40);

    private final ChatRepository chats = mock(ChatRepository.class);
    private final ChatMemberRepository chatMembers = mock(ChatMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final ChatService service = new ChatService(
        chats, chatMembers, messages, roomMembers, blocks, users, clock);

    private User me;
    private User friend;
    private User blocked;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저", "소프트웨어학과", true);
        friend = user(2L, "민트초코", "컴퓨터공학과", false);
        friend.getInterests().add(new Interest("보드게임", true));
        friend.getInterests().add(new Interest("비공개취미", false));
        blocked = user(3L, "차단유저", "경영학과", true);

        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));
        when(chatMembers.save(any(ChatMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chats.save(any(Chat.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 채팅방_목록은_차단된_DM을_제외하고_최신메시지와_안읽은수를_포함해_최신활동순으로_반환한다() {
        Room room = new Room(friend, "점심 보드게임", "보드게임", 2, 720, 810, WED, "학생회관", 4, null);
        room.setId(10L);
        Chat roomChat = Chat.forRoom(room);
        roomChat.setId(100L);
        roomChat.setCreatedAt(NOW.minusHours(3));

        Chat dmChat = Chat.dm();
        dmChat.setId(101L);
        dmChat.setCreatedAt(NOW.minusHours(2));

        Chat blockedDm = Chat.dm();
        blockedDm.setId(102L);
        blockedDm.setCreatedAt(NOW.minusMinutes(10));

        ChatMember cmRoom = new ChatMember(roomChat, me);
        cmRoom.setLastReadAt(NOW.minusMinutes(30));
        ChatMember cmDm = new ChatMember(dmChat, me);
        cmDm.setLastReadAt(null); // 한 번도 안 읽음 -> 전체 메시지 수
        ChatMember cmBlocked = new ChatMember(blockedDm, me);

        when(chatMembers.findByUserIdWithChat(1L)).thenReturn(List.of(cmRoom, cmDm, cmBlocked));
        when(chatMembers.findByChatIdInWithUser(anyCollection())).thenReturn(List.of(
            cmDm,
            new ChatMember(dmChat, friend),
            cmBlocked,
            new ChatMember(blockedDm, blocked)
        ));
        when(roomMembers.findByRoomIdInAndLeftAtIsNull(List.of(10L))).thenReturn(List.of(
            new RoomMember(room, friend),
            new RoomMember(room, me)
        ));

        Message mRoom = message(1L, roomChat, friend, "학생회관에서 봐요", NOW.minusMinutes(5));
        Message mDm = message(2L, dmChat, friend, "안녕하세요!", NOW.minusMinutes(1));
        when(messages.findLatestByChatIdIn(List.of(100L, 101L))).thenReturn(List.of(mRoom, mDm));
        when(messages.countByChatIdAndCreatedAtAfter(100L, NOW.minusMinutes(30))).thenReturn(1L);
        when(messages.countByChatId(101L)).thenReturn(3L);

        List<ChatSummaryResponse> res = service.list(1L);

        // blockedDm(102) 제외, 최신 메시지 순: dmChat(101) -> roomChat(100)
        assertThat(res).extracting(ChatSummaryResponse::id).containsExactly(101L, 100L);

        ChatSummaryResponse dmRes = res.get(0);
        assertThat(dmRes.kind()).isEqualTo("dm");
        assertThat(dmRes.otherUser().nick()).isEqualTo("민트초코");
        assertThat(dmRes.otherUser().dept()).isNull(); // 비공개 학과 마스킹
        assertThat(dmRes.otherUser().interests()).containsExactly("보드게임"); // 비공개 관심사 제외
        assertThat(dmRes.unreadCount()).isEqualTo(3L);
        assertThat(dmRes.lastMessage().text()).isEqualTo("안녕하세요!");

        ChatSummaryResponse roomRes = res.get(1);
        assertThat(roomRes.kind()).isEqualTo("room");
        assertThat(roomRes.roomId()).isEqualTo(10L);
        assertThat(roomRes.room().memberCount()).isEqualTo(2);
        assertThat(roomRes.room().status()).isEqualTo(RoomPhase.OPEN);
        assertThat(roomRes.unreadCount()).isEqualTo(1L);
    }

    @Test
    void 메시지_이력_조회_시_페이징_반환과_함께_lastReadAt을_갱신한다() {
        Chat dmChat = Chat.dm();
        dmChat.setId(101L);
        ChatMember myMember = new ChatMember(dmChat, me);

        when(chats.findWithRoomById(101L)).thenReturn(Optional.of(dmChat));
        when(chatMembers.findById(new ChatMemberId(101L, 1L))).thenReturn(Optional.of(myMember));
        when(chatMembers.findByChatIdWithUser(101L)).thenReturn(List.of(myMember, new ChatMember(dmChat, friend)));

        Message sys = message(1L, dmChat, null, "서로 공강이 겹치는 수 12:00–14:00에 만나요", NOW.minusMinutes(10));
        Message mine = message(2L, dmChat, me, "안녕하세요", NOW.minusMinutes(5));
        when(messages.findPageWithSenderByChatId(eq(101L), eq(PageRequest.of(0, 50))))
            .thenReturn(new PageImpl<>(List.of(mine, sys), PageRequest.of(0, 50), 2));

        ChatMessagesPageResponse res = service.getMessages(1L, 101L, 0, 50);

        assertThat(res.chatId()).isEqualTo(101L);
        assertThat(res.messages()).hasSize(2);
        assertThat(res.messages().get(0).mine()).isTrue();
        assertThat(res.messages().get(0).system()).isFalse();
        assertThat(res.messages().get(1).system()).isTrue();
        assertThat(res.messages().get(1).senderId()).isNull();
        assertThat(myMember.getLastReadAt()).isEqualTo(NOW);
        verify(chatMembers).save(myMember);
    }

    @Test
    void 비참여자나_차단된_DM_채팅방_접근을_거절한다() {
        Chat dmChat = Chat.dm();
        dmChat.setId(102L);
        when(chats.findWithRoomById(102L)).thenReturn(Optional.of(dmChat));

        // 비참여자 -> 403 CHAT_FORBIDDEN
        when(chatMembers.findById(new ChatMemberId(102L, 1L))).thenReturn(Optional.empty());
        assertCode(() -> service.getMessages(1L, 102L, 0, 50), ErrorCode.CHAT_FORBIDDEN);

        // 차단된 상대의 DM -> 404 CHAT_NOT_FOUND
        ChatMember myMember = new ChatMember(dmChat, me);
        when(chatMembers.findById(new ChatMemberId(102L, 1L))).thenReturn(Optional.of(myMember));
        when(chatMembers.findByChatIdWithUser(102L)).thenReturn(List.of(myMember, new ChatMember(dmChat, blocked)));
        assertCode(() -> service.getMessages(1L, 102L, 0, 50), ErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    void 오픈채팅_링크는_ROOM_채팅방에서만_유효한_URL로_설정하거나_비울_수_있다() {
        Room room = new Room(friend, "보드게임", "보드게임", 2, 720, 810, WED, "학생회관", 4, null);
        room.setId(10L);
        Chat roomChat = Chat.forRoom(room);
        roomChat.setId(100L);
        ChatMember myMember = new ChatMember(roomChat, me);

        when(chats.findWithRoomById(100L)).thenReturn(Optional.of(roomChat));
        when(chatMembers.findById(new ChatMemberId(100L, 1L))).thenReturn(Optional.of(myMember));
        when(roomMembers.countByRoomIdAndLeftAtIsNull(10L)).thenReturn(2L);
        when(messages.findLatestByChatIdIn(List.of(100L))).thenReturn(List.of());
        when(messages.countByChatId(100L)).thenReturn(0L);

        // javascript: 등 비허용 스킴 거절
        assertCode(() -> service.updateLink(1L, 100L, new UpdateChatLinkRequest("javascript:alert(1)")),
            ErrorCode.INVALID_INPUT);

        // 유효한 https 링크 저장
        ChatSummaryResponse updated = service.updateLink(1L, 100L,
            new UpdateChatLinkRequest("  https://open.kakao.com/o/abc123  "));
        assertThat(updated.link()).isEqualTo("https://open.kakao.com/o/abc123");
        assertThat(roomChat.getLink()).isEqualTo("https://open.kakao.com/o/abc123");

        // 빈 문자열로 링크 제거
        ChatSummaryResponse cleared = service.updateLink(1L, 100L, new UpdateChatLinkRequest("   "));
        assertThat(cleared.link()).isNull();
        assertThat(roomChat.getLink()).isNull();

        // DM 채팅방에서는 400 CHAT_LINK_NOT_ALLOWED
        Chat dmChat = Chat.dm();
        dmChat.setId(101L);
        ChatMember dmMember = new ChatMember(dmChat, me);
        when(chats.findWithRoomById(101L)).thenReturn(Optional.of(dmChat));
        when(chatMembers.findById(new ChatMemberId(101L, 1L))).thenReturn(Optional.of(dmMember));
        when(chatMembers.findByChatIdWithUser(101L)).thenReturn(List.of(dmMember, new ChatMember(dmChat, friend)));
        assertCode(() -> service.updateLink(1L, 101L, new UpdateChatLinkRequest("https://open.kakao.com/o/abc")),
            ErrorCode.CHAT_LINK_NOT_ALLOWED);
    }

    private static User user(Long id, String nick, String dept, boolean showDept) {
        User u = new User("hash-" + id, "SEED:u" + id + "@dankook.ac.kr", "단국대학교", nick);
        u.setId(id);
        u.setDept(dept);
        u.setShowDept(showDept);
        u.setManner(new BigDecimal("36.8"));
        return u;
    }

    private static Message message(Long id, Chat chat, User sender, String text, LocalDateTime createdAt) {
        Message m = new Message(chat, sender, text);
        m.setId(id);
        m.setCreatedAt(createdAt);
        return m;
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode())
            .isEqualTo(code);
    }
}
