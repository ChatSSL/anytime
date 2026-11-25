package com.chatssl.anytime.timetable.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/me/classes. 내 수업 전체 목록. 빈 목록이면 시간표를 비운다 */
public record ReplaceClassesRequest(
    @NotNull(message = "classes를 보내 주세요")
    @Size(max = 60, message = "수업은 60개까지 넣을 수 있어요")
    List<@Valid @NotNull ClassItem> classes
) {
}
