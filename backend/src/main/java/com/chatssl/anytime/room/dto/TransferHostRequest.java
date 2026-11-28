package com.chatssl.anytime.room.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/rooms/{id}/host 요청 */
public record TransferHostRequest(
    @NotNull(message = "새 방장을 골라 주세요")
    Long userId
) {
}
