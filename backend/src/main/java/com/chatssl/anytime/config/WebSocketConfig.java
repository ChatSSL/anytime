package com.chatssl.anytime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.chatssl.anytime.chat.StompAuthChannelInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * 14단계 Spring WebSocket + STOMP 설정.
 * - 핸드셰이크 엔드포인트: /ws/chat (네이티브 WebSocket, SockJS 미사용)
 * - 허용 Origin은 app.ws.allowed-origin-patterns(환경변수 WS_ALLOWED_ORIGINS)로 제어
 * - 브로커: /topic(채팅방 브로드캐스트), /queue(사용자별 에러 큐 /user/queue/errors)
 * - 인바운드 채널 인터셉터에서 CONNECT JWT 인증 및 SUBSCRIBE 권한 검증 수행
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${app.ws.allowed-origin-patterns:http://localhost:5173,http://localhost:8080,http://127.0.0.1:5173,http://127.0.0.1:8080}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
            .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
