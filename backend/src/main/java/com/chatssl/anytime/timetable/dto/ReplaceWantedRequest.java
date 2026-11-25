package com.chatssl.anytime.timetable.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/me/wanted. 만나고 싶은 공강 전체 목록. 빈 목록이면 아무 때도 만나지 않는다 */
public record ReplaceWantedRequest(
    @NotNull(message = "wanted를 보내 주세요")
    @Size(max = 60, message = "공강은 60개까지 고를 수 있어요")
    List<@Valid @NotNull SlotItem> wanted
) {
}
