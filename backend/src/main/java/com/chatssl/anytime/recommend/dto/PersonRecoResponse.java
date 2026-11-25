package com.chatssl.anytime.recommend.dto;

import java.util.List;

import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.user.dto.UserProfileResponse;

/**
 * GET /api/recommend/people 한 줄. 프론트 PersonReco와 같은 모양.
 * 상대 시간표는 넣지 않고, 나와 겹치는 공강(slots)만 넣는다.
 */
public record PersonRecoResponse(
    /** GET /api/users/{id}와 같은 공개 프로필 */
    UserProfileResponse user,
    /** 나와 60분 이상 겹치는 공강. 요일·시작 순 */
    List<SlotItem> slots,
    /** 둘 다 가진 관심 개념 (예: 보드게임, 카페공부). 상대의 공개 관심사만 쓴다 */
    List<String> shared,
    /** 0.55 × 겹친 시간(180분이면 만점) + 0.45 × 관심사 유사도 + 매너/1000 */
    double score,
    /** 예: 월요일 11:00–13:00 공강이 겹쳐요 · 보드게임 취향이 비슷해요 */
    String reason
) {
}
