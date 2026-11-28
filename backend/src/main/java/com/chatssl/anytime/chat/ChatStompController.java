package com.chatssl.anytime.chat;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.chatssl.anytime.chat.dto.ChatBroadcastMessage;
import com.chatssl.anytime.chat.dto.SendChatMessageRequest;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.ErrorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 14단계 STOMP 실시간 채팅 메시지 수신/브로드캐스트 컨트롤러.
 * - 클라이언트 송신 경로: /app/chat.send
 * - 브로드캐스트 경로: /topic/chat.{chatId}
 * - 트랜잭션이 커밋된 뒤에만 브로드캐스트하도록 @Transactional 서비스 메서드가 반환된 이후 convertAndSend를 호출한다.
 * - 송신 검증/도메인 오류는 /user/queue/errors로 {code, message}를 반환하여 WebSocket 세션을 유지한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(@Payload(required = false) SendChatMessageRequest request, Principal principal) {
        Long userId = StompAuthChannelInterceptor.extractUserId(principal);
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "메시지 요청을 확인해 주세요");
        }

        // ChatService.sendMessage(@Transactional)가 DB 커밋을 완료한 뒤 반환되면 브로드캐스트한다.
        ChatBroadcastMessage saved = chatService.sendMessage(userId, request.chatId(), request.text());
        messagingTemplate.convertAndSend("/topic/chat." + saved.chatId(), saved);
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleApiException(ApiException e) {
        return ErrorResponse.of(e.getErrorCode(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleUnexpected(Exception e) {
        log.warn("STOMP 메시지 처리 오류: {}", e.getClass().getSimpleName());
        return ErrorResponse.of(ErrorCode.INTERNAL_ERROR);
    }
}
