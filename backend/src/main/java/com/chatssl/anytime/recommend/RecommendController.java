package com.chatssl.anytime.recommend;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.recommend.dto.PersonRecoResponse;
import com.chatssl.anytime.recommend.dto.RoomRecoResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "추천", description = "공강이 겹치는 사람과 모임 (10단계). 프론트 derive.ts의 계산을 서버에서 한다")
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @Operation(summary = "사람 추천",
        description = "내 '만나고 싶은 공강'과 상대의 '만나고 싶은 공강'이 60분 이상 겹치는 사람. 점수 높은 순, 최대 50명. "
            + "점수 = 0.55 × min(1, 겹친 분/180) + 0.45 × 관심사 유사도 + 매너/1000. "
            + "상대 시간표는 주지 않고 겹치는 구간(slots)만 준다. 차단 관계(어느 쪽이든)·일시 정지 이상은 빠진다.")
    @GetMapping("/people")
    public List<PersonRecoResponse> people(@CurrentUserId Long userId) {
        return recommendService.people(userId);
    }

    @Operation(summary = "모임 추천",
        description = "지금 합류할 수 있는(status = open/soon) 모임 중 내가 참여하지 않은 것. "
            + "내 공강과 겹치는(fit > 0) 모임이 먼저, 그 안에서는 빨리 시작하는 순. 차단 관계인 사람이 방장인 모임은 빠진다. "
            + "방 상태는 서버의 지금 시각(한국 시간)으로 계산한다.")
    @GetMapping("/rooms")
    public List<RoomRecoResponse> rooms(@CurrentUserId Long userId) {
        return recommendService.rooms(userId);
    }
}
