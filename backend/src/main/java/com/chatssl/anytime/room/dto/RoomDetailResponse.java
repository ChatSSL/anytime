package com.chatssl.anytime.room.dto;

import java.time.LocalDate;
import java.util.List;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.user.dto.UserProfileResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /api/rooms, GET /api/rooms/{id} 상세 응답 */
public record RoomDetailResponse(
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
    @JsonProperty("isHost") boolean isHost,
    Long chatId,
    int fit,
    SlotItem slot,
    List<UserProfileResponse> members
) {

    public static RoomDetailResponse of(
        Room r,
        int memberCount,
        RoomPhase status,
        boolean joined,
        boolean isHost,
        Long chatId,
        int fit,
        SlotItem slot,
        List<UserProfileResponse> members
    ) {
        return new RoomDetailResponse(
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
            isHost,
            chatId,
            fit,
            slot,
            members
        );
    }
}
