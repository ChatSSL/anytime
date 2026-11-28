package com.chatssl.anytime.safety.dto;

/** POST /api/reports 응답. blocked=true면 긴급 신고라 차단까지 됐다 */
public record ReportResponse(
    Long id,
    boolean emergency,
    boolean blocked
) {
}
