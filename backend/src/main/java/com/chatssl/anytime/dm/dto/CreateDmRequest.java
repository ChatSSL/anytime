package com.chatssl.anytime.dm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/dm-requests 요청 */
public record CreateDmRequest(
    @NotNull(message = "상대방을 골라 주세요")
    Long targetUserId,

    @NotNull(message = "요일을 골라 주세요")
    @Min(value = 0, message = "요일은 월~금 중에서 골라 주세요")
    @Max(value = 4, message = "요일은 월~금 중에서 골라 주세요")
    Integer day,

    @NotNull(message = "시작 시간을 골라 주세요")
    @Min(value = 0, message = "시간은 00:00~24:00 사이로 정해 주세요")
    @Max(value = 1439, message = "시간은 00:00~24:00 사이로 정해 주세요")
    Integer start,

    @NotNull(message = "종료 시간을 골라 주세요")
    @Min(value = 1, message = "시간은 00:00~24:00 사이로 정해 주세요")
    @Max(value = 1440, message = "시간은 00:00~24:00 사이로 정해 주세요")
    Integer end,

    @Size(max = 20, message = "활동은 20자까지 쓸 수 있어요")
    String activity,

    @Size(max = 200, message = "메시지는 200자까지 쓸 수 있어요")
    String message
) {
}
