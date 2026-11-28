package com.chatssl.anytime.roomchange;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.roomchange.dto.ChangeResponse;
import com.chatssl.anytime.roomchange.dto.ProposeChangeRequest;
import com.chatssl.anytime.roomchange.dto.VoteRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "일정 변경 투표", description = "방장의 일정 변경 제안과 참여자 동의/불참 (15단계)")
@RestController
@RequiredArgsConstructor
public class RoomChangeController {

    private final RoomChangeService roomChangeService;

    @Operation(summary = "일정 변경 제안 (방장)",
        description = "day 0~4(월~금), start/end는 0시부터 분. place를 비우면 장소는 그대로. "
            + "다른 참여자에게 '답변 필요' 알림이 간다. 다른 참여자가 없으면 바로 확정(APPLIED). "
            + "방장 아님 403 CHANGE_HOST_ONLY, 이미 대기 중 409 CHANGE_ALREADY_PENDING, 시작 30분 전부터 409 ROOM_CLOSED")
    @PostMapping("/api/rooms/{roomId}/changes")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeResponse propose(
        @CurrentUserId Long userId,
        @PathVariable Long roomId,
        @Valid @RequestBody ProposeChangeRequest request
    ) {
        return roomChangeService.propose(userId, roomId, request);
    }

    @Operation(summary = "답변을 기다리는 일정 변경",
        description = "있으면 200, 없으면 204. 모임 상세 화면의 '일정이 바뀌었어요' 배너용")
    @GetMapping("/api/rooms/{roomId}/changes/pending")
    public ResponseEntity<ChangeResponse> pending(@CurrentUserId Long userId, @PathVariable Long roomId) {
        return roomChangeService.pending(userId, roomId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "일정 변경에 답하기 (참여자)",
        description = "accept=true '그래도 참여할게요', false '이번엔 빠질게요'(모임에서 나감, 매너 점수 차감 없음). "
            + "답해야 하는 사람이 모두 답하면 일정이 확정된다. 방장은 400 CHANGE_HOST_NO_VOTE, 두 번째 답은 409 CHANGE_ALREADY_VOTED")
    @PostMapping("/api/changes/{changeId}/vote")
    public ChangeResponse vote(
        @CurrentUserId Long userId,
        @PathVariable Long changeId,
        @Valid @RequestBody VoteRequest request
    ) {
        return roomChangeService.vote(userId, changeId, request.accept());
    }
}
