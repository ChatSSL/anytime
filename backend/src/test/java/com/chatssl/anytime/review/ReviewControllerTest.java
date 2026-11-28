package com.chatssl.anytime.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.CurrentUserIdResolver;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.common.GlobalExceptionHandler;
import com.chatssl.anytime.review.dto.PendingReviewResponse;
import com.chatssl.anytime.review.dto.ReviewFormResponse;
import com.chatssl.anytime.review.dto.ReviewResultResponse;
import com.chatssl.anytime.review.dto.SubmitReviewRequest;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.user.dto.UserProfileResponse;

class ReviewControllerTest {

    private final ReviewService reviewService = mock(ReviewService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
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
    void 목록과_평가_화면() throws Exception {
        when(reviewService.pending(1L)).thenReturn(new PendingReviewResponse(List.of(), List.of(11L)));
        UserProfileResponse u2 = new UserProfileResponse(2L, "민트초코", null, 1, List.of(), 36.8, 3);
        when(reviewService.form(1L, 10L)).thenReturn(new ReviewFormResponse(
            10L, "보드게임 한 판", "보드게임", LocalDate.of(2026, 11, 25), 540, 600, RoomPhase.ENDED,
            2, List.of(u2), false, true, ReviewService.LOW_REASONS));

        mvc.perform(get("/api/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewedRoomIds[0]").value(11));

        mvc.perform(get("/api/rooms/10/review"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ended"))
            .andExpect(jsonPath("$.members[0].nick").value("민트초코"))
            .andExpect(jsonPath("$.canReview").value(true));
    }

    @Test
    void 제출() throws Exception {
        when(reviewService.submit(eq(1L), eq(10L), any(SubmitReviewRequest.class)))
            .thenReturn(new ReviewResultResponse(10L, 1, 1, 2, false));

        mvc.perform(post("/api/rooms/10/review").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"presentUserIds": [2], "ratings": [{"targetUserId": 2, "score": 2, "reason": "기타"}], "note": "또 봐요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedRatings").value(1))
            .andExpect(jsonPath("$.myCompleted").value(false));

        ArgumentCaptor<SubmitReviewRequest> req = ArgumentCaptor.forClass(SubmitReviewRequest.class);
        verify(reviewService).submit(eq(1L), eq(10L), req.capture());
        assertThat(req.getValue().ratings().get(0).reason()).isEqualTo("기타");
        assertThat(req.getValue().note()).isEqualTo("또 봐요");
    }

    @Test
    void 점수가_범위를_벗어나면_400() throws Exception {
        mvc.perform(post("/api/rooms/10/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentUserIds\": [2], \"ratings\": [{\"targetUserId\": 2, \"score\": 6}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(reviewService, never()).submit(any(), any(), any());
    }

    @Test
    void 서비스_에러는_코드로_내려간다() throws Exception {
        when(reviewService.submit(eq(1L), eq(10L), any(SubmitReviewRequest.class)))
            .thenThrow(new ApiException(ErrorCode.REVIEW_ALREADY_DONE));

        mvc.perform(post("/api/rooms/10/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentUserIds\": [], \"ratings\": []}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_DONE"));
    }
}
