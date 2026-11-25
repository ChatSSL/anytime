package com.chatssl.anytime.timetable.dto;

import com.chatssl.anytime.domain.Slot;

import jakarta.validation.constraints.NotNull;

/** 만나고 싶은 공강 한 칸. 프론트 Slot과 같은 모양 */
public record SlotItem(
    @NotNull Integer day,
    @NotNull Integer start,
    @NotNull Integer end
) {

    public static SlotItem from(Slot s) {
        return new SlotItem(s.getDay(), s.getStartMin(), s.getEndMin());
    }

    public Slot toSlot() {
        return new Slot(day, start, end);
    }
}
