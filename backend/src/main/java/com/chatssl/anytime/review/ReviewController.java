package com.chatssl.anytime.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.review.dto.PendingReviewResponse;
import com.chatssl.anytime.review.dto.ReviewFormResponse;
import com.chatssl.anytime.review.dto.ReviewResultResponse;
import com.chatssl.anytime.review.dto.SubmitReviewRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "참석 확인·평가", description = "모임이 끝난 뒤 참석 확인과 상호 평가 (16단계)")
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "평가할 모임 목록",
        description = "최근 7일 안에 끝났고 아직 평가하지 않은 내 모임(pending)과 이미 평가한 모임 id. 홈 배너·모임 탭 버튼용")
    @GetMapping("/api/reviews")
    public PendingReviewResponse pending(@CurrentUserId Long userId) {
        return reviewService.pending(userId);
    }

    @Operation(summary = "평가 화면 정보",
        description = "나를 뺀 참여자, 이미 평가했는지(reviewed), 지금 낼 수 있는지(canReview), 2점 이하 이유 목록. "
            + "참여 중이 아니면 400 ROOM_NOT_MEMBER")
    @GetMapping("/api/rooms/{roomId}/review")
    public ReviewFormResponse form(@CurrentUserId Long userId, @PathVariable Long roomId) {
        return reviewService.form(userId, roomId);
    }

    @Operation(summary = "참석 확인 + 평가 제출",
        description = "presentUserIds: 왔다고 확인한 사람(나는 자동 포함). ratings: 온 사람(나 제외) 모두 1~5점, "
            + "2점 이하는 reason 필수. 한 모임에 한 번만(409 REVIEW_ALREADY_DONE). "
            + "끝나기 전 409 REVIEW_NOT_ENDED, 7일 지나면 409 REVIEW_EXPIRED. "
            + "점수는 바로 반영하지 않고 크론이 모아서 매너 점수에 넣는다")
    @PostMapping("/api/rooms/{roomId}/review")
    public ReviewResultResponse submit(
        @CurrentUserId Long userId,
        @PathVariable Long roomId,
        @Valid @RequestBody SubmitReviewRequest request
    ) {
        return reviewService.submit(userId, roomId, request);
    }
}
