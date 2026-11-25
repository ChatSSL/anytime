package com.chatssl.anytime.timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.ClassBlock;
import com.chatssl.anytime.domain.Slot;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.ClassBlockRepository;
import com.chatssl.anytime.repository.UserRepository;
import com.chatssl.anytime.timetable.dto.ClassItem;
import com.chatssl.anytime.timetable.dto.SlotItem;
import com.chatssl.anytime.timetable.dto.TimetableResponse;

import lombok.RequiredArgsConstructor;

/**
 * 9단계: 내 시간표(수업)와 만나고 싶은 공강.
 * 둘 다 "전체 목록을 한 번에 교체"한다. 프론트가 수업 하나를 추가·삭제해도 목록 전체를 보낸다.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    static final int MAX_DAY = 4;
    static final int MINUTES_PER_DAY = 1440;
    static final int NAME_MAX = 50;
    private static final String DAYS = "월화수목금";

    private static final Comparator<ClassItem> CLASS_ORDER =
        Comparator.comparing(ClassItem::day).thenComparing(ClassItem::start).thenComparing(ClassItem::end);
    private static final Comparator<SlotItem> SLOT_ORDER =
        Comparator.comparing(SlotItem::day).thenComparing(SlotItem::start).thenComparing(SlotItem::end);

    private final UserRepository userRepository;
    private final ClassBlockRepository classBlockRepository;

    @Transactional(readOnly = true)
    public TimetableResponse get(Long userId) {
        User user = findUser(userId);
        return response(classBlockRepository.findByUserIdOrderByDayAscStartMinAsc(userId), user.getWantedSlots());
    }

    /**
     * 수업 목록 전체 교체. 서로 겹치는 수업이 있으면 400 TIMETABLE_CLASS_OVERLAP.
     * 새 수업과 겹치게 된 '만나고 싶은 공강'은 같이 지운다(수업 시간에 만날 수는 없으니까).
     */
    @Transactional
    public TimetableResponse replaceClasses(Long userId, List<ClassItem> items) {
        User user = findUser(userId);

        List<ClassItem> cleaned = new ArrayList<>();
        for (ClassItem item : items) {
            checkRange(item.day(), item.start(), item.end());
            cleaned.add(new ClassItem(null, cleanName(item.name()), item.day(), item.start(), item.end()));
        }
        cleaned.sort(CLASS_ORDER);
        checkNoOverlap(cleaned);

        classBlockRepository.deleteByUserId(userId);
        List<ClassBlock> saved = classBlockRepository.saveAll(cleaned.stream()
            .map(c -> new ClassBlock(user, c.name(), c.day(), c.start(), c.end()))
            .toList());

        user.getWantedSlots().removeIf(slot -> saved.stream()
            .anyMatch(c -> slot.overlaps(c.getDay(), c.getStartMin(), c.getEndMin())));

        return response(saved, user.getWantedSlots());
    }

    /**
     * 만나고 싶은 공강 전체 교체. 같은 칸은 하나로 합치고,
     * 공강끼리 겹치거나 내 수업과 겹치면 400.
     */
    @Transactional
    public TimetableResponse replaceWanted(Long userId, List<SlotItem> items) {
        User user = findUser(userId);
        List<ClassBlock> classes = classBlockRepository.findByUserIdOrderByDayAscStartMinAsc(userId);

        List<SlotItem> unique = new ArrayList<>(new LinkedHashSet<>(items));
        unique.sort(SLOT_ORDER);
        for (int i = 0; i < unique.size(); i++) {
            SlotItem s = unique.get(i);
            checkRange(s.day(), s.start(), s.end());
            if (i > 0) {
                SlotItem prev = unique.get(i - 1);
                if (prev.day().equals(s.day()) && s.start() < prev.end()) {
                    throw new ApiException(ErrorCode.INVALID_INPUT,
                        label(prev.day(), prev.start(), prev.end()) + "와 " + label(s.day(), s.start(), s.end()) + "이 겹쳐요");
                }
            }
            for (ClassBlock c : classes) {
                if (c.getDay().equals(s.day()) && s.start() < c.getEndMin() && c.getStartMin() < s.end()) {
                    throw new ApiException(ErrorCode.TIMETABLE_WANTED_NOT_FREE,
                        label(s.day(), s.start(), s.end()) + "에 " + nameOf(c.getName()) + " 수업이 있어요");
                }
            }
        }

        Set<Slot> wanted = user.getWantedSlots();
        wanted.clear();
        unique.forEach(s -> wanted.add(s.toSlot()));
        return response(classes, wanted);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /** 요일 0~4, 0 <= 시작 < 끝 <= 24:00 */
    static void checkRange(int day, int start, int end) {
        if (day < 0 || day > MAX_DAY) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "요일은 월~금 중에서 골라 주세요");
        }
        if (start < 0 || end > MINUTES_PER_DAY) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "시간은 00:00~24:00 사이로 정해 주세요");
        }
        if (start >= end) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "종료 시간이 시작보다 늦어야 해요");
        }
    }

    /** 요일·시작 순으로 정렬된 목록에서 바로 앞 수업과 겹치는지만 보면 된다 */
    static void checkNoOverlap(List<ClassItem> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            ClassItem prev = sorted.get(i - 1);
            ClassItem cur = sorted.get(i);
            if (prev.day().equals(cur.day()) && cur.start() < prev.end()) {
                throw new ApiException(ErrorCode.TIMETABLE_CLASS_OVERLAP,
                    nameOf(prev.name()) + "(" + label(prev.day(), prev.start(), prev.end()) + ")와 "
                        + nameOf(cur.name()) + "(" + label(cur.day(), cur.start(), cur.end()) + ") 수업 시간이 겹쳐요");
            }
        }
    }

    /** 앞뒤 공백 제거, 빈 이름은 null(화면에서 '수업'으로 보인다) */
    private static String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim();
        if (name.length() > NAME_MAX) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "과목명은 " + NAME_MAX + "자까지 쓸 수 있어요");
        }
        return name;
    }

    private static String nameOf(String name) {
        return name == null ? "수업" : name;
    }

    /** 예: 월 09:00–10:30 */
    static String label(int day, int start, int end) {
        return DAYS.charAt(day) + " " + hm(start) + "–" + hm(end);
    }

    private static String hm(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private static TimetableResponse response(List<ClassBlock> classes, Set<Slot> wanted) {
        return new TimetableResponse(
            classes.stream().map(ClassItem::from).sorted(CLASS_ORDER).toList(),
            wanted.stream().map(SlotItem::from).sorted(SLOT_ORDER).toList());
    }
}
