package com.chatssl.anytime.safety.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/blocks 요청 */
public record BlockRequest(
    @NotNull(message = "차단할 사람을 골라 주세요")
    Long targetUserId
) {
}
