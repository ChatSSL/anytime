package com.chatssl.anytime.room.dto;

import java.time.LocalDate;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.timetable.dto.SlotItem;

/** GET /api/rooms 목록 한 줄 */
public record RoomResponse(
    Long id,
    String title,
    String activity,
    int day,
    int start,
    int end,
    LocalDate meetDate,
    String place,
    int cap,
    int memberCount,
    Long hostId,
    String hostNick,
    String topic,
    boolean official,
    RoomPhase status,
    boolean joined,
    int fit,
    SlotItem slot
) {

    public static RoomResponse of(Room r, int memberCount, RoomPhase status, boolean joined, int fit, SlotItem slot) {
        return new RoomResponse(
            r.getId(),
            r.getTitle(),
            r.getActivity(),
            r.getDay(),
            r.getStartMin(),
            r.getEndMin(),
            r.getMeetDate(),
            r.getPlace(),
            r.getCap(),
            memberCount,
            r.getHost().getId(),
            r.getHost().getNick(),
            r.getTopic(),
            r.isOfficial(),
            status,
            joined,
            fit,
            slot
        );
    }
}
