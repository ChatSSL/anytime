package com.chatssl.anytime.room;

import java.time.LocalDateTime;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.domain.RoomStatus;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 조회 시점에 계산하는 방 상태. 프론트 derive.ts의 roomStatus()를 옮겼다. DB에는 저장하지 않는다.
 * JSON으로는 프론트 RoomStatus와 같은 소문자('open', 'soon', ...)로 나간다.
 *
 * 프론트는 요일+분(시연용 시계)으로 비교하지만, 서버는 실제 날짜(meet_date + start_min)와 지금 시각으로 비교한다.
 * 10단계 추천에서 만들었고, 11단계 모임 목록·상세도 이걸 쓰면 된다.
 */
public enum RoomPhase {
    OPEN("open"),
    /** 시작 1시간 전부터 */
    SOON("soon"),
    /** 시작 30분 전 모집 마감 */
    CLOSED("closed"),
    FULL("full"),
    ENDED("ended"),
    CANCELLED("cancelled");

    /** 시작 시각에 이보다 적으면 자동 취소 (운영팀 모임 제외) */
    public static final int MIN_MEMBERS = 2;
    public static final int CLOSE_BEFORE_MIN = 30;
    public static final int SOON_BEFORE_MIN = 60;

    private final String value;

    RoomPhase(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /** 지금 합류할 수 있는 상태인지 (프론트 joinable) */
    public boolean joinable() {
        return this == OPEN || this == SOON;
    }

    /**
     * @param memberCount 지금 참여 중인 인원 (나간 사람 제외)
     * @param now 한국 시간 기준 지금 (LocalDateTime.now(clock))
     */
    public static RoomPhase of(Room room, int memberCount, LocalDateTime now) {
        // 취소·종료가 저장돼 있으면 시간과 상관없이 그대로
        if (room.getStatus() == RoomStatus.CANCELED) {
            return CANCELLED;
        }
        if (room.getStatus() == RoomStatus.DONE) {
            return ENDED;
        }
        LocalDateTime start = room.getMeetDate().atStartOfDay().plusMinutes(room.getStartMin());
        LocalDateTime end = room.getMeetDate().atStartOfDay().plusMinutes(room.getEndMin());

        if (!room.isOfficial() && memberCount < MIN_MEMBERS && !now.isBefore(start)) {
            return CANCELLED;
        }
        if (!now.isBefore(end)) {
            return ENDED;
        }
        if (memberCount >= room.getCap()) {
            return FULL;
        }
        if (!now.isBefore(start.minusMinutes(CLOSE_BEFORE_MIN))) {
            return CLOSED;
        }
        if (!now.isBefore(start.minusMinutes(SOON_BEFORE_MIN))) {
            return SOON;
        }
        return OPEN;
    }
}
