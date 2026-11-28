package com.chatssl.anytime.dm;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.dm.dto.CreateDmRequest;
import com.chatssl.anytime.dm.dto.DmRequestListResponse;
import com.chatssl.anytime.dm.dto.DmRequestResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "1:1 요청", description = "1:1 만남 요청 전송·조회·수락·거절 (13단계)")
@RestController
@RequestMapping("/api/dm-requests")
@RequiredArgsConstructor
public class DmRequestController {

    private final DmRequestService dmRequestService;

    @Operation(summary = "1:1 만남 요청 보내기",
        description = "상대방에게 겹치는 공강 시간으로 1:1 요청을 보낸다. 기존 DM이 있거나 양방향 PENDING 요청이 있으면 409를 반환한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DmRequestResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateDmRequest request) {
        return dmRequestService.create(userId, request);
    }

    @Operation(summary = "1:1 만남 요청 목록",
        description = "받은 대기 요청(received)과 내가 보낸 요청(sent)을 한 번에 반환한다. 차단 관계 사용자는 제외한다.")
    @GetMapping
    public DmRequestListResponse list(@CurrentUserId Long userId) {
        return dmRequestService.list(userId);
    }

    @Operation(summary = "1:1 만남 요청 수락",
        description = "요청을 수락하고 DM 채팅방을 생성(또는 기존 DM 재사용)하며 초기 시스템·요청 메시지와 수락 알림을 남긴다.")
    @PostMapping("/{id}/accept")
    public DmRequestResponse accept(@CurrentUserId Long userId, @PathVariable Long id) {
        return dmRequestService.accept(userId, id);
    }

    @Operation(summary = "1:1 만남 요청 거절",
        description = "요청을 거절하고 연결된 알림을 거절 처리한다.")
    @PostMapping("/{id}/decline")
    public DmRequestResponse decline(@CurrentUserId Long userId, @PathVariable Long id) {
        return dmRequestService.decline(userId, id);
    }
}
