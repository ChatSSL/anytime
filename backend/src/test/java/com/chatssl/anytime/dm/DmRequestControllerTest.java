package com.chatssl.anytime.dm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.CurrentUserIdResolver;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.GlobalExceptionHandler;
import com.chatssl.anytime.dm.dto.CreateDmRequest;
import com.chatssl.anytime.dm.dto.DmRequestListResponse;
import com.chatssl.anytime.dm.dto.DmRequestResponse;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class DmRequestControllerTest {

    private final DmRequestService dmRequestService = mock(DmRequestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DmRequestController(dmRequestService))
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
    void 일대일_요청_생성_조회_수락_거절_엔드포인트가_정상_동작한다() throws Exception {
        UserProfileResponse u1 = new UserProfileResponse(1L, "데모유저", "소프트웨어학과", 0, List.of("보드게임"), 36.8, 3);
        UserProfileResponse u2 = new UserProfileResponse(2L, "민트초코", "컴퓨터공학과", 1, List.of("보드게임"), 37.2, 8);
        LocalDateTime now = LocalDateTime.of(2026, 11, 25, 10, 40);

        DmRequestResponse pending = new DmRequestResponse(10L, u1, u2, 2, 720, 840, "보드게임", "같이 해요", "pending", null, now);
        DmRequestResponse accepted = new DmRequestResponse(10L, u2, u1, 2, 720, 840, "보드게임", "같이 해요", "accepted", 300L, now);
        DmRequestResponse declined = new DmRequestResponse(11L, u2, u1, 2, 720, 840, "보드게임", null, "declined", null, now);

        when(dmRequestService.create(eq(1L), any(CreateDmRequest.class))).thenReturn(pending);
        when(dmRequestService.list(1L)).thenReturn(new DmRequestListResponse(List.of(pending), List.of()));
        when(dmRequestService.accept(1L, 10L)).thenReturn(accepted);
        when(dmRequestService.decline(1L, 11L)).thenReturn(declined);

        mvc.perform(post("/api/dm-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2,
                      "day": 2,
                      "start": 720,
                      "end": 840,
                      "activity": "보드게임",
                      "message": "같이 해요"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.status").value("pending"));

        mvc.perform(get("/api/dm-requests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received[0].id").value(10))
            .andExpect(jsonPath("$.sent").isArray());

        mvc.perform(post("/api/dm-requests/10/accept"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.chatId").value(300));

        mvc.perform(post("/api/dm-requests/11/decline"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("declined"));
    }

    @Test
    void 중복_DM_요청은_409_DM_REQUEST_ALREADY_EXISTS_입력누락은_400_INVALID_INPUT() throws Exception {
        when(dmRequestService.create(eq(1L), any(CreateDmRequest.class)))
            .thenThrow(new ApiException(ErrorCode.DM_REQUEST_ALREADY_EXISTS));

        mvc.perform(post("/api/dm-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetUserId": 2, "day": 2, "start": 720, "end": 840}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DM_REQUEST_ALREADY_EXISTS"));

        mvc.perform(post("/api/dm-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"day": 2, "start": 720, "end": 840}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
