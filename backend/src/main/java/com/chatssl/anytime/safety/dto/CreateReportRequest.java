package com.chatssl.anytime.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/reports 요청. reason은 프론트 REPORT_REASONS 중 하나, 긴급 신고면 "긴급 신고" */
public record CreateReportRequest(
    @NotNull(message = "신고할 사람을 골라 주세요")
    Long targetUserId,

    @NotBlank(message = "신고 사유를 골라 주세요")
    @Size(max = 50, message = "신고 사유를 목록에서 골라 주세요")
    String reason,

    @Size(max = 1000, message = "상세 내용은 1000자까지 쓸 수 있어요")
    String detail,

    /** 어느 모임에서 생긴 일인지 (선택) */
    Long roomId,

    /** true면 긴급 신고. 바로 차단까지 한다 */
    Boolean emergency
) {
}
