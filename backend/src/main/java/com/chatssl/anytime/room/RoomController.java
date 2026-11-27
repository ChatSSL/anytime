package com.chatssl.anytime.room;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.room.dto.CreateRoomRequest;
import com.chatssl.anytime.room.dto.RoomDetailResponse;
import com.chatssl.anytime.room.dto.RoomResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "모임", description = "모임 목록·상세·생성 및 동적 상태 (11단계)")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "모임 만들기",
        description = "모임을 열고 모임방 채팅과 방장 참여 기록을 함께 만든다. meetDate는 서버가 가장 가까운 해당 요일로 계산한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDetailResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateRoomRequest request) {
        return roomService.create(userId, request);
    }

    @Operation(summary = "모임 목록",
        description = "요일·활동·장소·시작 시각·최소 정원·시간대·내 공강 겹침 여부로 필터링한다. 차단 관계인 방장의 모임은 빠지고, 상태(RoomPhase)는 조회 시점에 계산한다.")
    @GetMapping
    public List<RoomResponse> list(
        @CurrentUserId Long userId,
        @RequestParam(required = false) Integer day,
        @RequestParam(required = false) List<String> activity,
        @RequestParam(required = false) List<String> place,
        @RequestParam(required = false) Integer afterMin,
        @RequestParam(required = false) Integer minCap,
        @RequestParam(required = false) Integer start,
        @RequestParam(required = false) Integer end,
        @RequestParam(required = false) Boolean fitOnly,
        @RequestParam(required = false) Boolean mine
    ) {
        return roomService.list(userId, day, activity, place, afterMin, minCap, start, end, fitOnly, mine);
    }

    @Operation(summary = "모임 상세",
        description = "동적 상태(RoomPhase)와 마스킹된 참여자 목록(차단 관계 제외), 연결된 채팅방 id를 반환한다.")
    @GetMapping("/{id}")
    public RoomDetailResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return roomService.detail(userId, id);
    }
}
