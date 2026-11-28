package com.chatssl.anytime.chat;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.chatssl.anytime.auth.jwt.JwtProvider;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.Chat;
import com.chatssl.anytime.domain.ChatKind;
import com.chatssl.anytime.domain.ChatMember;
import com.chatssl.anytime.repository.BlockRepository;
import com.chatssl.anytime.repository.ChatMemberRepository;
import com.chatssl.anytime.repository.ChatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 14단계 STOMP 인바운드 보안 인터셉터.
 * - CONNECT: 네이티브 헤더 Authorization: Bearer <JWT> 검증 및 Principal(Long userId) 주입 (원문 토큰 로깅 금지)
 * - SUBSCRIBE: /user/queue/errors 또는 /topic/chat.{chatId}만 허용하며, 채팅 참여 여부 및 DM 양방향 차단 검증
 * - SEND: 인증된 Principal 존재 여부 1차 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String USER_ERROR_QUEUE = "/user/queue/errors";
    private static final Pattern CHAT_TOPIC_PATTERN = Pattern.compile("^/topic/chat\\.(\\d+)$");
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final BlockRepository blockRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            handleConnect(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            handleSubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            if (extractUserId(accessor.getUser()) == null) {
                throw new MessageDeliveryException(ErrorCode.UNAUTHORIZED.name());
            }
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            authHeader = accessor.getFirstNativeHeader("authorization");
        }
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            log.warn("STOMP CONNECT 거부: Authorization Bearer 헤더 없음");
            throw new MessageDeliveryException(ErrorCode.AUTH_TOKEN_INVALID.name());
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            log.warn("STOMP CONNECT 거부: 빈 Bearer 토큰");
            throw new MessageDeliveryException(ErrorCode.AUTH_TOKEN_INVALID.name());
        }

        Long userId;
        try {
            userId = jwtProvider.parseAccessToken(token);
        } catch (ApiException e) {
            // 보안 규칙: 원문 JWT 문자열은 절대 로깅하지 않는다.
            log.warn("STOMP CONNECT 인증 실패: code={}", e.getErrorCode().name());
            throw new MessageDeliveryException(e.getErrorCode().name());
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        accessor.setUser(auth);
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Long userId = extractUserId(accessor.getUser());
        if (userId == null) {
            throw new MessageDeliveryException(ErrorCode.UNAUTHORIZED.name());
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new MessageDeliveryException(ErrorCode.CHAT_FORBIDDEN.name());
        }
        if (USER_ERROR_QUEUE.equals(destination)) {
            return;
        }

        Matcher matcher = CHAT_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            log.warn("STOMP SUBSCRIBE 거부: 허용되지 않은 대상 userId={}, destination={}", userId, destination);
            throw new MessageDeliveryException(ErrorCode.CHAT_FORBIDDEN.name());
        }

        long chatId;
        try {
            chatId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new MessageDeliveryException(ErrorCode.CHAT_FORBIDDEN.name());
        }

        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            log.warn("STOMP SUBSCRIBE 거부: 채팅방 미참여 userId={}, chatId={}", userId, chatId);
            throw new MessageDeliveryException(ErrorCode.CHAT_FORBIDDEN.name());
        }

        Chat chat = chatRepository.findById(chatId)
            .orElseThrow(() -> new MessageDeliveryException(ErrorCode.CHAT_NOT_FOUND.name()));

        if (chat.getKind() == ChatKind.DM) {
            Set<Long> hidden = blockRepository.findHiddenUserIds(userId);
            if (!hidden.isEmpty()) {
                boolean blocked = chatMemberRepository.findByChatIdWithUser(chatId).stream()
                    .map(ChatMember::getUser)
                    .anyMatch(u -> !u.getId().equals(userId) && hidden.contains(u.getId()));
                if (blocked) {
                    log.warn("STOMP SUBSCRIBE 거부: 차단 관계 DM userId={}, chatId={}", userId, chatId);
                    throw new MessageDeliveryException(ErrorCode.CHAT_NOT_FOUND.name());
                }
            }
        }
    }

    static Long extractUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }
}
