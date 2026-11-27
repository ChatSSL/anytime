package com.chatssl.anytime.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/rooms 요청 */
public record CreateRoomRequest(
    @NotBlank(message = "제목을 입력해 주세요")
    @Size(max = 50, message = "제목은 50자까지 쓸 수 있어요")
    String title,

    @NotBlank(message = "활동을 골라 주세요")
    @Size(max = 20, message = "활동은 20자까지 쓸 수 있어요")
    String activity,

    @NotNull(message = "요일을 골라 주세요")
    @Min(value = 0, message = "요일은 월~금 중에서 골라 주세요")
    @Max(value = 4, message = "요일은 월~금 중에서 골라 주세요")
    Integer day,

    @NotNull(message = "시작 시간을 입력해 주세요")
    @Min(value = 0, message = "시간은 00:00~24:00 사이로 정해 주세요")
    @Max(value = 1439, message = "시간은 00:00~24:00 사이로 정해 주세요")
    Integer start,

    @NotNull(message = "종료 시간을 입력해 주세요")
    @Min(value = 1, message = "시간은 00:00~24:00 사이로 정해 주세요")
    @Max(value = 1440, message = "시간은 00:00~24:00 사이로 정해 주세요")
    Integer end,

    @NotBlank(message = "장소를 입력해 주세요")
    @Size(max = 50, message = "장소는 50자까지 쓸 수 있어요")
    String place,

    @NotNull(message = "정원을 입력해 주세요")
    @Min(value = 2, message = "정원은 2~8명 사이로 정해 주세요")
    @Max(value = 8, message = "정원은 2~8명 사이로 정해 주세요")
    Integer cap,

    @Size(max = 200, message = "소개는 200자까지 쓸 수 있어요")
    String topic
) {
}
