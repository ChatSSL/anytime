package com.chatssl.anytime.roomchange.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/changes/{changeId}/vote. accept=false면 '이번엔 빠질게요' → 모임에서 나간다(매너 점수 차감 없음) */
public record VoteRequest(
    @NotNull(message = "참여할지 골라 주세요")
    Boolean accept
) {
}
