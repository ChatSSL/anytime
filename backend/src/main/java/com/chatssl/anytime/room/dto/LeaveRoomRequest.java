package com.chatssl.anytime.room.dto;

import jakarta.validation.constraints.Size;

/** POST /api/rooms/{id}/leave 요청 */
public record LeaveRoomRequest(
    @Size(max = 100, message = "사유는 100자까지 쓸 수 있어요")
    String reason
) {
}
