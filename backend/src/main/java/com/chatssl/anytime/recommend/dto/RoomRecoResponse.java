package com.chatssl.anytime.recommend.dto;

import java.time.LocalDate;

import com.chatssl.anytime.domain.Room;
import com.chatssl.anytime.room.RoomPhase;
import com.chatssl.anytime.timetable.dto.SlotItem;

/** GET /api/recommend/rooms 한 줄. 프론트 recommendRooms()의 {room, status, fit} */
public record RoomRecoResponse(
    RoomSummary room,
    /** 'open' 또는 'soon' (합류할 수 있는 방만 추천한다) */
    RoomPhase status,
    /** 모임 시간 중 내 공강과 겹치는 비율 0~100 */
    int fit,
    /** 가장 많이 겹치는 내 공강 구간. 안 겹치면 null */
    SlotItem slot
) {

    /** 모임 카드에 필요한 것만. 참여자 목록은 넣지 않는다(11단계 상세 API에서) */
    public record RoomSummary(
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
        boolean official
    ) {

        public static RoomSummary of(Room r, int memberCount) {
            return new RoomSummary(r.getId(), r.getTitle(), r.getActivity(), r.getDay(), r.getStartMin(), r.getEndMin(),
                r.getMeetDate(), r.getPlace(), r.getCap(), memberCount, r.getHost().getId(), r.getHost().getNick(),
                r.getTopic(), r.isOfficial());
        }
    }
}
