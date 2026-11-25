package com.chatssl.anytime.timetable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.common.CurrentUserId;
import com.chatssl.anytime.timetable.dto.ReplaceClassesRequest;
import com.chatssl.anytime.timetable.dto.ReplaceWantedRequest;
import com.chatssl.anytime.timetable.dto.TimetableResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "시간표", description = "내 수업과 만나고 싶은 공강 (9단계). 본인 것만 조회·수정한다")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @Operation(summary = "내 시간표",
        description = "수업 목록과 만나고 싶은 공강 목록. 시간은 요일(0=월~4=금) + 하루 중 분(540 = 09:00)")
    @GetMapping("/timetable")
    public TimetableResponse timetable(@CurrentUserId Long userId) {
        return timetableService.get(userId);
    }

    @Operation(summary = "수업 목록 전체 교체",
        description = "보낸 목록으로 통째로 바꾼다(빈 목록이면 비우기). 수업끼리 겹치면 400 TIMETABLE_CLASS_OVERLAP. "
            + "새 수업과 겹치는 '만나고 싶은 공강'은 같이 지워지고, 바뀐 시간표 전체를 돌려준다.")
    @PutMapping("/classes")
    public TimetableResponse replaceClasses(@CurrentUserId Long userId, @Valid @RequestBody ReplaceClassesRequest request) {
        return timetableService.replaceClasses(userId, request.classes());
    }

    @Operation(summary = "만나고 싶은 공강 전체 교체",
        description = "추천(10단계)은 이 목록에 있는 시간만 쓴다. 내 수업과 겹치면 400 TIMETABLE_WANTED_NOT_FREE, "
            + "공강끼리 겹치면 400 INVALID_INPUT.")
    @PutMapping("/wanted")
    public TimetableResponse replaceWanted(@CurrentUserId Long userId, @Valid @RequestBody ReplaceWantedRequest request) {
        return timetableService.replaceWanted(userId, request.wanted());
    }
}
