package com.chatssl.anytime.safety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.chatssl.anytime.safety.dto.BlockedUserResponse;
import com.chatssl.anytime.safety.dto.CreateReportRequest;
import com.chatssl.anytime.safety.dto.ReportResponse;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class SafetyControllerTest {

    private final SafetyService safetyService = mock(SafetyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SafetyController(safetyService))
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
    void 차단_목록_차단_해제_엔드포인트() throws Exception {
        UserProfileResponse u2 = new UserProfileResponse(2L, "민트초코", "컴퓨터공학과", 1, List.of("보드게임"), 37.2, 8);
        BlockedUserResponse row = new BlockedUserResponse(u2, LocalDateTime.of(2026, 11, 25, 10, 40));
        when(safetyService.blocks(1L)).thenReturn(List.of(row));
        when(safetyService.block(1L, 2L)).thenReturn(row);

        mvc.perform(get("/api/blocks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user.nick").value("민트초코"));

        mvc.perform(post("/api/blocks").contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\": 2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.id").value(2));

        mvc.perform(delete("/api/blocks/2"))
            .andExpect(status().isNoContent());
        verify(safetyService).unblock(1L, 2L);
    }

    @Test
    void 차단_대상이_없으면_400() throws Exception {
        mvc.perform(post("/api/blocks").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 신고_엔드포인트와_에러_코드() throws Exception {
        when(safetyService.report(eq(1L), any(CreateReportRequest.class))).thenReturn(new ReportResponse(500L, false, false));

        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\": 2, \"reason\": \"기타\", \"detail\": \"내용\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(500));

        when(safetyService.report(eq(1L), any(CreateReportRequest.class)))
            .thenThrow(new ApiException(ErrorCode.REPORT_TOO_SOON));
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\": 2, \"reason\": \"기타\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("REPORT_TOO_SOON"));
    }
}
