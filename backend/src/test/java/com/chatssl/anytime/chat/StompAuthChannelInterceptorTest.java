package com.chatssl.anytime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.chatssl.anytime.auth.jwt.JwtProvider;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;

class StompAuthChannelInterceptorTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final ChatRepository chatRepository = mock(ChatRepository.class);
    private final ChatMemberRepository chatMemberRepository = mock(ChatMemberRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);

    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
        jwtProvider, chatRepository, chatMemberRepository, blockRepository
    );

    @Test
    void CONNECT_유효한_Bearer_JWT면_Principal을_설정한다() {
        when(jwtProvider.parseAccessToken("valid-jwt")).thenReturn(1L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-jwt");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(msg, channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor out = StompHeaderAccessor.wrap(result);
        assertThat(out.getUser()).isInstanceOf(Authentication.class);
        Authentication auth = (Authentication) out.getUser();
        assertThat(auth.getPrincipal()).isEqualTo(1L);
        assertThat(auth.getName()).isEqualTo("1");
    }

    @Test
    void CONNECT_Authorization_헤더가_없으면_거부한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.AUTH_TOKEN_INVALID.name());
        verifyNoInteractions(jwtProvider);
    }

    @Test
    void CONNECT_만료된_JWT는_AUTH_TOKEN_EXPIRED로_거부한다() {
        when(jwtProvider.parseAccessToken("expired-jwt"))
            .thenThrow(new ApiException(ErrorCode.AUTH_TOKEN_EXPIRED));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer expired-jwt");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.AUTH_TOKEN_EXPIRED.name());
    }

    @Test
    void CONNECT_위변조된_JWT는_AUTH_TOKEN_INVALID로_거부한다() {
        when(jwtProvider.parseAccessToken("bad-jwt"))
            .thenThrow(new ApiException(ErrorCode.AUTH_TOKEN_INVALID));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad-jwt");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.AUTH_TOKEN_INVALID.name());
    }

    @Test
    void SUBSCRIBE_참여중인_모임방_구독을_허용한다() {
        Chat roomChat = Chat.forRoom(mock(Room.class));
        roomChat.setId(10L);
        when(chatMemberRepository.existsByChatIdAndUserId(10L, 1L)).thenReturn(true);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(roomChat));

        Message<byte[]> msg = subscribeMessage(1L, "/topic/chat.10");

        assertThat(interceptor.preSend(msg, channel)).isSameAs(msg);
    }

    @Test
    void SUBSCRIBE_인증된_사용자의_에러큐_구독을_허용한다() {
        Message<byte[]> msg = subscribeMessage(1L, "/user/queue/errors");

        assertThat(interceptor.preSend(msg, channel)).isSameAs(msg);
        verifyNoInteractions(chatMemberRepository, chatRepository, blockRepository);
    }

    @Test
    void SUBSCRIBE_비참여자는_거부한다() {
        when(chatMemberRepository.existsByChatIdAndUserId(10L, 1L)).thenReturn(false);

        Message<byte[]> msg = subscribeMessage(1L, "/topic/chat.10");

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.CHAT_FORBIDDEN.name());
    }

    @Test
    void SUBSCRIBE_양방향_차단된_DM_채팅방은_거부한다() {
        User me = user(1L, "나");
        User blocked = user(3L, "차단유저");
        Chat dmChat = Chat.dm();
        dmChat.setId(20L);

        when(chatMemberRepository.existsByChatIdAndUserId(20L, 1L)).thenReturn(true);
        when(chatRepository.findById(20L)).thenReturn(Optional.of(dmChat));
        when(blockRepository.findHiddenUserIds(1L)).thenReturn(Set.of(3L));
        when(chatMemberRepository.findByChatIdWithUser(20L))
            .thenReturn(List.of(new ChatMember(dmChat, me), new ChatMember(dmChat, blocked)));

        Message<byte[]> msg = subscribeMessage(1L, "/topic/chat.20");

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.CHAT_NOT_FOUND.name());
    }

    @Test
    void SUBSCRIBE_잘못된_destination_형식은_거부한다() {
        Message<byte[]> msg = subscribeMessage(1L, "/topic/chat.abc");

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.CHAT_FORBIDDEN.name());
        verifyNoInteractions(chatMemberRepository);
    }

    @Test
    void SUBSCRIBE_미인증_세션은_거부한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat.10");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
            .isInstanceOf(MessageDeliveryException.class)
            .hasMessageContaining(ErrorCode.UNAUTHORIZED.name());
    }

    private static Message<byte[]> subscribeMessage(Long userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static User user(Long id, String nick) {
        User u = new User("hash-" + id, "enc-" + id, "단국대학교", nick);
        u.setId(id);
        return u;
    }
}
