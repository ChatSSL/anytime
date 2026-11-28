package com.chatssl.anytime.safety;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.safety.dto.BlockRequest;
import com.chatssl.anytime.safety.dto.BlockedUserResponse;
import com.chatssl.anytime.safety.dto.CreateReportRequest;
import com.chatssl.anytime.safety.dto.ReportResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "신고·차단", description = "차단·차단 해제·차단 목록, 신고 (17단계)")
@RestController
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @Operation(summary = "내가 차단한 사람 목록", description = "최근 차단한 순. 마이페이지 차단 목록 화면용")
    @GetMapping("/api/blocks")
    public List<BlockedUserResponse> blocks(@CurrentUserId Long userId) {
        return safetyService.blocks(userId);
    }

    @Operation(summary = "차단",
        description = "차단하면 서로 추천·1:1 요청에서 빠진다(양방향). 이미 차단했으면 그대로 성공. 본인은 400 BLOCK_SELF")
    @PostMapping("/api/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedUserResponse block(@CurrentUserId Long userId, @Valid @RequestBody BlockRequest request) {
        return safetyService.block(userId, request.targetUserId());
    }

    @Operation(summary = "차단 해제", description = "차단한 적이 없어도 204")
    @DeleteMapping("/api/blocks/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@CurrentUserId Long userId, @PathVariable Long targetUserId) {
        safetyService.unblock(userId, targetUserId);
    }

    @Operation(summary = "신고",
        description = "reason은 프론트 신고 사유 목록 중 하나(긴급 신고면 \"긴급 신고\" + emergency=true). "
            + "같은 사람은 24시간에 한 번(429 REPORT_TOO_SOON, 긴급 신고는 예외). 긴급 신고는 바로 차단까지 한다")
    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@CurrentUserId Long userId, @Valid @RequestBody CreateReportRequest request) {
        return safetyService.report(userId, request);
    }
}
