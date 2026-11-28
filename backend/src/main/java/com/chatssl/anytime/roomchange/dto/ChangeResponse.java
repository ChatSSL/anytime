package com.chatssl.anytime.roomchange.dto;

import java.time.LocalDate;

import com.chatssl.anytime.domain.ChangeStatus;

/** 일정 변경 한 건. current*는 지금 모임 일정(확정 전이면 바뀌기 전 일정) */
public record ChangeResponse(
    Long id,
    Long roomId,
    /** PENDING(답변 대기) / APPLIED(확정) / REJECTED(무산) */
    ChangeStatus status,
    int newDay,
    int newStart,
    int newEnd,
    /** null이면 장소는 그대로 */
    String newPlace,
    int currentDay,
    int currentStart,
    int currentEnd,
    String currentPlace,
    LocalDate currentMeetDate,
    /** 답해야 하는 사람 수 (방장과 제안 뒤에 들어온 사람 제외) */
    int required,
    /** 그중 '그래도 참여할게요'를 고른 사람 수 */
    int accepted,
    /** 내 답. 아직 안 했으면 null */
    Boolean myVote,
    /** 내가 지금 답할 수 있는지 */
    boolean canVote,
    boolean isHost
) {
}
