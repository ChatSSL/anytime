package com.chatssl.anytime.review.dto;

/** POST /api/rooms/{roomId}/review 결과 */
public record ReviewResultResponse(
    Long roomId,
    /** 이번에 저장한 평가 수. 매너 점수에는 크론(18단계)이 모아서 반영한다 */
    int savedRatings,
    /** 나의 참석을 확인해 준 사람 수 (나 포함) */
    int myConfirmations,
    int memberCount,
    /** 과반이 확인해서 내 모임 완료가 기록됐는지 */
    boolean myCompleted
) {
}
