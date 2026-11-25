package com.chatssl.anytime.timetable.dto;

import com.chatssl.anytime.domain.ClassBlock;

import jakarta.validation.constraints.NotNull;

/**
 * 수업 한 칸. 프론트 ClassBlock과 같은 모양(day 0=월~4=금, start·end는 하루 중 분).
 * 요청에서는 id를 무시하고, 응답에서는 서버 id를 준다.
 */
public record ClassItem(
    Long id,
    String name,
    @NotNull Integer day,
    @NotNull Integer start,
    @NotNull Integer end
) {

    public static ClassItem from(ClassBlock c) {
        return new ClassItem(c.getId(), c.getName(), c.getDay(), c.getStartMin(), c.getEndMin());
    }
}
