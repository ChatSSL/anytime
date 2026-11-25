package com.chatssl.anytime.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class TimetableServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ClassBlockRepository classes = mock(ClassBlockRepository.class);
    private final TimetableService service = new TimetableService(users, classes);

    private User me;

    @BeforeEach
    void setUp() {
        me = new User("hash", "SEED:me@dankook.ac.kr", "단국대", "데모유저");
        when(users.findById(1L)).thenReturn(Optional.of(me));
        when(classes.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ClassItem cls(String name, int day, int start, int end) {
        return new ClassItem(null, name, day, start, end);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((ApiException) t).getErrorCode();
    }

    @Test
    void 수업을_요일_시간_순으로_저장하고_빈_이름은_null로_바꾼다() {
        TimetableResponse res = service.replaceClasses(1L, List.of(
            cls("알고리즘", 1, 540, 630),
            cls("  ", 0, 780, 870),
            cls(" 자료구조 ", 0, 540, 630)));

        verify(classes).deleteByUserId(1L);
        assertThat(res.classes()).extracting(ClassItem::name).containsExactly("자료구조", null, "알고리즘");
        assertThat(res.classes()).extracting(ClassItem::day).containsExactly(0, 0, 1);
    }

    @Test
    void 겹치는_수업이_있으면_저장하지_않는다() {
        assertThatThrownBy(() -> service.replaceClasses(1L, List.of(
            cls("자료구조", 0, 540, 630),
            cls("운영체제", 0, 600, 690))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TIMETABLE_CLASS_OVERLAP))
            .hasMessageContaining("자료구조")
            .hasMessageContaining("운영체제");
        verify(classes, never()).deleteByUserId(1L);
    }

    @Test
    void 끝나는_시간에_바로_시작하는_수업은_겹치지_않는다() {
        TimetableResponse res = service.replaceClasses(1L, List.of(
            cls("A", 2, 540, 630),
            cls("B", 2, 630, 720)));
        assertThat(res.classes()).hasSize(2);
    }

    @Test
    void 요일과_시간_범위를_검사한다() {
        assertThatThrownBy(() -> service.replaceClasses(1L, List.of(cls("주말", 5, 540, 630))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.replaceClasses(1L, List.of(cls("거꾸로", 0, 630, 540))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.replaceClasses(1L, List.of(cls("자정넘김", 0, 1400, 1500))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 새_수업과_겹치게_된_공강은_같이_지운다() {
        me.getWantedSlots().add(new Slot(0, 630, 780));   // 월 10:30–13:00
        me.getWantedSlots().add(new Slot(1, 630, 780));   // 화 10:30–13:00

        TimetableResponse res = service.replaceClasses(1L, List.of(cls("점심특강", 0, 720, 780)));

        assertThat(res.wanted()).containsExactly(new SlotItem(1, 630, 780));
        assertThat(me.getWantedSlots()).containsExactly(new Slot(1, 630, 780));
    }

    @Test
    void 공강은_통째로_바꾸고_같은_칸은_하나로_합친다() {
        me.getWantedSlots().add(new Slot(4, 600, 700));
        when(classes.findByUserIdOrderByDayAscStartMinAsc(1L)).thenReturn(List.of());

        TimetableResponse res = service.replaceWanted(1L, List.of(
            new SlotItem(2, 630, 780), new SlotItem(0, 630, 780), new SlotItem(2, 630, 780)));

        assertThat(res.wanted()).containsExactly(new SlotItem(0, 630, 780), new SlotItem(2, 630, 780));
        assertThat(me.getWantedSlots()).hasSize(2);
    }

    @Test
    void 수업_시간과_겹치는_공강은_고를_수_없다() {
        when(classes.findByUserIdOrderByDayAscStartMinAsc(1L))
            .thenReturn(List.of(new ClassBlock(me, "자료구조", 0, 540, 630)));

        assertThatThrownBy(() -> service.replaceWanted(1L, List.of(new SlotItem(0, 600, 720))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TIMETABLE_WANTED_NOT_FREE))
            .hasMessageContaining("자료구조");
    }

    @Test
    void 공강끼리_겹치면_거절한다() {
        when(classes.findByUserIdOrderByDayAscStartMinAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceWanted(1L, List.of(new SlotItem(3, 600, 720), new SlotItem(3, 700, 800))))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void 없는_사용자는_404() {
        when(users.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L))
            .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
