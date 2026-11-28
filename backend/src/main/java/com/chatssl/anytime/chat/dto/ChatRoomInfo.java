package com.chatssl.anytime.chat.dto;

import java.time.LocalDate;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.room.RoomPhase;

/** 모임방 채팅의 연결된 모임 요약 정보 */
public record ChatRoomInfo(
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
    RoomPhase status
) {

    public static ChatRoomInfo of(Room room, int memberCount, RoomPhase phase) {
        return new ChatRoomInfo(
            room.getId(),
            room.getTitle(),
            room.getActivity(),
            room.getDay(),
            room.getStartMin(),
            room.getEndMin(),
            room.getMeetDate(),
            room.getPlace(),
            room.getCap(),
            memberCount,
            phase
        );
    }
}
