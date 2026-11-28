package com.chatssl.anytime.review.dto;

import java.util.List;

import com.chatssl.anytime.room.dto.RoomResponse;

/** GET /api/reviews: 평가할 모임과 이미 평가한 모임 id */
public record PendingReviewResponse(
    /** 끝났고 아직 평가하지 않은 내 모임 (최근 7일, 최근 것부터) */
    List<RoomResponse> pending,
    /** 최근 7일 안에 평가를 마친 모임 id */
    List<Long> reviewedRoomIds
) {
}
