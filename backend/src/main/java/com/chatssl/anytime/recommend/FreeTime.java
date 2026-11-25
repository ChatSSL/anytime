package com.chatssl.anytime.recommend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.chatssl.anytime.domain.Slot;
import com.chatssl.anytime.timetable.dto.SlotItem;

/**
 * 공강 겹침 계산. 프론트 src/lib/time.ts의 overlap(), intersectAll()을 그대로 옮겼다.
 * 결과 순서까지 프론트와 같아야 추천 이유("월요일 11:00–13:00 공강이 겹쳐요")가 같게 나온다.
 */
final class FreeTime {

    private static final String DAYS = "월화수목금";
    static final Comparator<SlotItem> ORDER =
        Comparator.comparing(SlotItem::day).thenComparing(SlotItem::start).thenComparing(SlotItem::end);

    private FreeTime() {
    }

    /** 같은 요일에서 겹치는 구간. 안 겹치면(끝과 시작이 맞닿기만 해도) null */
    static SlotItem overlap(SlotItem a, SlotItem b) {
        if (!a.day().equals(b.day())) {
            return null;
        }
        int start = Math.max(a.start(), b.start());
        int end = Math.min(a.end(), b.end());
        return end > start ? new SlotItem(a.day(), start, end) : null;
    }

    /**
     * 두 사람의 공강이 min분 이상 겹치는 구간만. 다른 사용자에게는 이 결과만 보낸다(시간표 원본은 안 보낸다).
     * 순서: a의 순서대로, 그 안에서 b의 순서대로 (프론트와 같다)
     */
    static List<SlotItem> intersectAll(List<SlotItem> a, List<SlotItem> b, int min) {
        List<SlotItem> out = new ArrayList<>();
        for (SlotItem x : a) {
            for (SlotItem y : b) {
                SlotItem o = overlap(x, y);
                if (o != null && o.end() - o.start() >= min) {
                    out.add(o);
                }
            }
        }
        return out;
    }

    /** wanted_slots(Set, 순서 없음) → 요일·시작 순 목록. 프론트 freeSlots()가 이 순서로 나온다 */
    static List<SlotItem> sorted(Collection<Slot> slots) {
        return slots.stream().map(SlotItem::from).sorted(ORDER).toList();
    }

    /** 예: 월요일 11:00–13:00 */
    static String label(SlotItem s) {
        return DAYS.charAt(s.day()) + "요일 " + hm(s.start()) + "–" + hm(s.end());
    }

    static String hm(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }
}
