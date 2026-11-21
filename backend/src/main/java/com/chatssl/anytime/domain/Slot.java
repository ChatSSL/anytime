package com.chatssl.anytime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 요일(0=월~4=금) + 분 단위 시간 구간. wanted_slots 한 줄. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class Slot {

    @Column(name = "day", nullable = false)
    private Integer day;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    public boolean overlaps(int otherDay, int otherStart, int otherEnd) {
        return day == otherDay && startMin < otherEnd && endMin > otherStart;
    }
}
