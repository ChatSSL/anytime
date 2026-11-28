package com.chatssl.anytime.review.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/rooms/{roomId}/review: 참석 확인과 평가를 한 번에 낸다.
 * presentUserIds에 나를 넣지 않아도 서버가 나는 자동으로 '왔음'으로 기록한다.
 */
public record SubmitReviewRequest(
    @NotNull(message = "온 사람을 골라 주세요")
    @Size(max = 20, message = "참여자는 20명까지예요")
    List<Long> presentUserIds,

    @NotNull(message = "평가를 보내 주세요")
    @Size(max = 20, message = "참여자는 20명까지예요")
    @Valid
    List<RatingItem> ratings,

    /** 한 줄 후기 (선택). 이번에 낸 평가 행마다 note로 저장한다 */
    @Size(max = 60, message = "한 줄 후기는 60자까지 쓸 수 있어요")
    String note
) {

    public record RatingItem(
        @NotNull(message = "평가할 사람을 골라 주세요")
        Long targetUserId,

        @NotNull(message = "점수를 골라 주세요")
        @Min(value = 1, message = "점수는 1~5점이에요")
        @Max(value = 5, message = "점수는 1~5점이에요")
        Integer score,

        /** 2점 이하면 필수. lowReasons 중 하나 */
        @Size(max = 50, message = "이유를 목록에서 골라 주세요")
        String reason
    ) {
    }
}
