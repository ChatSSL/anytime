package com.chatssl.anytime.roomchange.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/rooms/{roomId}/changes: 방장의 일정 변경 제안. place를 비우면 장소는 그대로 */
public record ProposeChangeRequest(
    @NotNull(message = "요일을 골라 주세요")
    Integer day,

    @NotNull(message = "시작 시간을 골라 주세요")
    Integer start,

    @NotNull(message = "끝나는 시간을 골라 주세요")
    Integer end,

    @Size(max = 50, message = "장소는 50자까지 쓸 수 있어요")
    String place
) {
}
