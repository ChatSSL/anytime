package com.chatssl.anytime.timetable.dto;

import java.util.List;

/**
 * 내 시간표 전체. 본인에게만 주는 응답이다(다른 사람의 시간표 원본은 어떤 API도 내려보내지 않는다).
 * classes는 요일·시작 시간 순, wanted도 같은 순서.
 */
public record TimetableResponse(List<ClassItem> classes, List<SlotItem> wanted) {
}
