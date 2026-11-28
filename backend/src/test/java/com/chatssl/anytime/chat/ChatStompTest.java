package com.chatssl.anytime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.chatssl.anytime.chat.dto.ChatBroadcastMessage;
import com.chatssl.anytime.chat.dto.SendChatMessageRequest;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.ErrorResponse;
import com.chatssl.anytime.config.TimeConfig;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.ChatMemberId;
import com.chatssl.anytime.domain.Message;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;
import com.chatssl.anytime.repository.MessageRepository;
import com.chatssl.anytime.repository.RoomMemberRepository;
import com.chatssl.anytime.repository.UserRepository;

class ChatStompTest {

    private static final LocalDate WED = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime NOW = WED.atTime(11, 15);

    private final ChatRepository chats = mock(ChatRepository.class);
    private final ChatMemberRepository chatMembers = mock(ChatMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(NOW.atZone(TimeConfig.ZONE).toInstant(), TimeConfig.ZONE);

    private final ChatService chatService = new ChatService(
        chats, chatMembers, messages, roomMembers, blocks, users, clock
    );

    private User me;
    private User blocked;

    @BeforeEach
    void setUp() {
        me = user(1L, "데모유저", 0);
        blocked = user(3L, "차단유저", 0);

        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(blocks.findHiddenUserIds(1L)).thenReturn(Set.of(3L));
        when(chatMembers.save(any(ChatMember.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sendMessage_정상_메시지를_저장하고_발신자_lastReadAt을_갱신한다() {
        Room room = new Room(me, "점심 보드게임", "보드게임", 2, 720, 810, WED, "학생회관", 4, null);
        room.setId(10L);
        Chat roomChat = Chat.forRoom(room);
        roomChat.setId(100L);
        ChatMember member = new ChatMember(roomChat, me);

        when(chats.findWithRoomById(100L)).thenReturn(Optional.of(roomChat));
        when(chatMembers.findById(new ChatMemberId(100L, 1L))).thenReturn(Optional.of(member));
        when(messages.saveAndFlush(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(501L);
            m.setCreatedAt(NOW);
            return m;
        });

        ChatBroadcastMessage res = chatService.sendMessage(1L, 100L, "  안녕하세요!  ");

        assertThat(res.id()).isEqualTo(501L);
        assertThat(res.chatId()).isEqualTo(100L);
        assertThat(res.senderId()).isEqualTo(1L);
        assertThat(res.senderNick()).isEqualTo("데모유저");
        assertThat(res.system()).isFalse();
        assertThat(res.text()).isEqualTo("안녕하세요!");
        assertThat(res.createdAt()).isEqualTo(NOW);
        assertThat(member.getLastReadAt()).isEqualTo(NOW);
        verify(messages).saveAndFlush(any(Message.class));
        verify(chatMembers).save(member);
    }

    @Test
    void sendMessage_정지된_사용자는_USER_SUSPENDED를_반환한다() {
        User suspended = user(9L, "정지유저", 2);
        when(users.findById(9L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> chatService.sendMessage(9L, 100L, "안녕"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.USER_SUSPENDED);
        verifyNoInteractions(messages);
    }

    @Test
    void sendMessage_공백이거나_1000자_초과면_INVALID_INPUT을_반환한다() {
        assertThatThrownBy(() -> chatService.sendMessage(1L, 100L, "   "))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);

        String overLimit = "a".repeat(1001);
        assertThatThrownBy(() -> chatService.sendMessage(1L, 100L, overLimit))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(messages);
    }

    @Test
    void sendMessage_비참여자는_CHAT_FORBIDDEN을_반환한다() {
        Chat roomChat = Chat.forRoom(mock(Room.class));
        roomChat.setId(100L);
        when(chats.findWithRoomById(100L)).thenReturn(Optional.of(roomChat));
        when(chatMembers.findById(new ChatMemberId(100L, 1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(1L, 100L, "안녕"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
        verifyNoInteractions(messages);
    }

    @Test
    void sendMessage_양방향_차단된_DM은_거부하고_저장하지_않는다() {
        Chat dmChat = Chat.dm();
        dmChat.setId(200L);
        ChatMember member = new ChatMember(dmChat, me);

        when(chats.findWithRoomById(200L)).thenReturn(Optional.of(dmChat));
        when(chatMembers.findById(new ChatMemberId(200L, 1L))).thenReturn(Optional.of(member));
        when(chatMembers.findByChatIdWithUser(200L))
            .thenReturn(List.of(member, new ChatMember(dmChat, blocked)));

        assertThatThrownBy(() -> chatService.sendMessage(1L, 200L, "안녕"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CHAT_NOT_FOUND);
        verifyNoInteractions(messages);
    }

    @Test
    void ChatStompController_DB저장_완료_후에만_토픽으로_브로드캐스트한다() {
        ChatService mockService = mock(ChatService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        ChatStompController controller = new ChatStompController(mockService, template);

        ChatBroadcastMessage saved = new ChatBroadcastMessage(501L, 100L, 1L, "데모유저", false, "안녕", NOW);
        when(mockService.sendMessage(1L, 100L, "안녕")).thenReturn(saved);

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        controller.send(new SendChatMessageRequest(100L, "안녕"), principal);

        InOrder order = inOrder(mockService, template);
        order.verify(mockService).sendMessage(1L, 100L, "안녕");
        order.verify(template).convertAndSend("/topic/chat.100", saved);
    }

    @Test
    void ChatStompController_저장_실패시_브로드캐스트하지_않고_예외핸들러가_에러응답을_반환한다() {
        ChatService mockService = mock(ChatService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        ChatStompController controller = new ChatStompController(mockService, template);

        ApiException ex = new ApiException(ErrorCode.CHAT_FORBIDDEN);
        when(mockService.sendMessage(1L, 100L, "안녕")).thenThrow(ex);

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThatThrownBy(() -> controller.send(new SendChatMessageRequest(100L, "안녕"), principal))
            .isSameAs(ex);
        verifyNoInteractions(template);

        ErrorResponse err = controller.handleApiException(ex);
        assertThat(err.code()).isEqualTo(ErrorCode.CHAT_FORBIDDEN.name());
        assertThat(err.message()).isEqualTo(ErrorCode.CHAT_FORBIDDEN.getMessage());
    }

    private static User user(Long id, String nick, int sanction) {
        User u = new User("hash-" + id, "enc-" + id, "단국대학교", nick);
        u.setId(id);
        u.setSanction(sanction);
        return u;
    }
}
