package com.chatssl.anytime.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chatssl.anytime.chat.dto.ChatMessageResponse;
import com.chatssl.anytime.chat.dto.ChatMessagesPageResponse;
import com.chatssl.anytime.chat.dto.ChatRoomInfo;
import com.chatssl.anytime.chat.dto.ChatSummaryResponse;
import com.chatssl.anytime.chat.dto.UpdateChatLinkRequest;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.CurrentUserIdResolver;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.GlobalExceptionHandler;
import com.chatssl.anytime.room.RoomPhase;

class ChatControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserIdResolver())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 채팅방_목록과_메시지_이력_읽음처리_오픈채팅링크_엔드포인트가_정상_응답한다() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 40);
        ChatMessageResponse msg = new ChatMessageResponse(5L, 10L, 2L, "민트초코", false, false, "안녕하세요", now);
        ChatRoomInfo roomInfo = new ChatRoomInfo(10L, "보드게임", "보드게임", 2, 720, 810, LocalDate.of(2026, 11, 25),
            "학생회관", 4, 2, RoomPhase.OPEN);
        ChatSummaryResponse summary = new ChatSummaryResponse(
            10L, "room", 10L, roomInfo, null, "https://open.kakao.com/o/abc", null, false, msg, 1L, now);

        when(chatService.list(1L)).thenReturn(List.of(summary));
        when(chatService.getMessages(1L, 10L, 0, 20)).thenReturn(
            new ChatMessagesPageResponse(10L, List.of(msg), 0, 20, 1L, 1, false));
        when(chatService.updateLink(eq(1L), eq(10L), any(UpdateChatLinkRequest.class))).thenReturn(summary);

        mvc.perform(get("/api/chats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].kind").value("room"))
            .andExpect(jsonPath("$[0].unreadCount").value(1));

        mvc.perform(get("/api/chats/10/messages").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chatId").value(10))
            .andExpect(jsonPath("$.messages[0].text").value("안녕하세요"));

        mvc.perform(post("/api/chats/10/read"))
            .andExpect(status().isNoContent());
        verify(chatService).markRead(1L, 10L);

        mvc.perform(patch("/api/chats/10/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"link": "https://open.kakao.com/o/abc"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.link").value("https://open.kakao.com/o/abc"));
    }

    @Test
    void 권한이_없는_채팅방_접근은_403_CHAT_FORBIDDEN() throws Exception {
        when(chatService.getMessages(1L, 99L, 0, 50)).thenThrow(new ApiException(ErrorCode.CHAT_FORBIDDEN));

        mvc.perform(get("/api/chats/99/messages"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CHAT_FORBIDDEN"));
    }
}
