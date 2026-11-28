package com.chatssl.anytime.review.dto;

import java.time.LocalDate;
import java.util.List;

import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.user.dto.UserProfileResponse;

/** GET /api/rooms/{roomId}/review: 평가 화면을 그릴 정보 */
public record ReviewFormResponse(
    Long roomId,
    String title,
    String activity,
    LocalDate meetDate,
    int start,
    int end,
    RoomPhase status,
    /** 지금 참여 중인 인원 (나 포함). 과반 계산의 분모 */
    int memberCount,
    /** 나를 뺀 참여자 (차단 관계는 제외) */
    List<UserProfileResponse> members,
    /** 이미 평가를 마쳤는지 */
    boolean reviewed,
    /** 지금 평가를 낼 수 있는지 (끝났고, 7일이 안 지났고, 아직 안 냈음) */
    boolean canReview,
    /** 프론트가 2점 이하일 때 보여줄 이유 목록 */
    List<String> lowReasons
) {
}
