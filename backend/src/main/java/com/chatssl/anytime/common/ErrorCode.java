package com.chatssl.anytime.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 응답의 code 목록. 프론트는 이 code로 화면 문구를 고른다.
 * 새 에러가 필요하면 여기에 추가한다(담당 도메인 이름을 앞에 붙이기).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요"),
    CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청이에요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요"),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정이에요"),
    USER_NICK_TAKEN(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임이에요"),

    // 이메일 인증 (5단계)
    AUTH_EMAIL_DOMAIN(HttpStatus.BAD_REQUEST, "학교 이메일만 사용할 수 있어요"),
    AUTH_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 1분 뒤에 다시 받을 수 있어요"),
    AUTH_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요"),
    AUTH_CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증번호를 먼저 받아 주세요"),
    AUTH_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료됐어요. 다시 받아 주세요"),
    AUTH_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요"),
    AUTH_CODE_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "5번 틀렸어요. 인증번호를 다시 받아 주세요"),

    // 로그인 토큰 (7단계). 프론트는 AUTH_TOKEN_EXPIRED를 받으면 /auth/refresh 후 다시 요청한다
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않아요"),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),

    // 시간표 (9단계)
    TIMETABLE_CLASS_OVERLAP(HttpStatus.BAD_REQUEST, "수업 시간이 겹쳐요"),
    TIMETABLE_WANTED_NOT_FREE(HttpStatus.BAD_REQUEST, "수업이 있는 시간은 고를 수 없어요"),

    // 모임
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없어요"),
    ROOM_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여 중인 모임이에요"),
    ROOM_NOT_MEMBER(HttpStatus.BAD_REQUEST, "참여 중인 모임이 아니에요"),
    ROOM_FULL(HttpStatus.CONFLICT, "정원이 가득 찬 모임이에요"),
    ROOM_CLOSED(HttpStatus.CONFLICT, "모집이 마감된 모임이에요"),
    ROOM_ENDED(HttpStatus.CONFLICT, "이미 끝난 모임이에요"),
    ROOM_CANCELLED(HttpStatus.CONFLICT, "취소된 모임이에요"),
    ROOM_HOST_TRANSFER_INVALID(HttpStatus.BAD_REQUEST, "방장을 넘길 수 없는 참여자예요"),

    // 채팅 (13단계)
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없어요"),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "참여 중인 채팅방이 아니에요"),
    CHAT_LINK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "모임 채팅방에서만 오픈채팅 링크를 설정할 수 있어요"),

    // 1:1 요청 (13단계)
    DM_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "1:1 요청을 찾을 수 없어요"),
    DM_REQUEST_SELF(HttpStatus.BAD_REQUEST, "본인에게는 1:1 요청을 보낼 수 없어요"),
    DM_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 1:1 요청이 있어요"),
    DM_REQUEST_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 1:1 요청이에요"),
    DM_CHAT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 연결된 1:1 채팅방이 있어요"),

    // 신고·차단·알림 (17단계)
    BLOCK_SELF(HttpStatus.BAD_REQUEST, "본인은 차단할 수 없어요"),
    REPORT_SELF(HttpStatus.BAD_REQUEST, "본인은 신고할 수 없어요"),
    REPORT_REASON_INVALID(HttpStatus.BAD_REQUEST, "신고 사유를 목록에서 골라 주세요"),
    REPORT_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "같은 사람은 하루에 한 번만 신고할 수 있어요"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없어요"),

    // 참석 확인·상호 평가 (16단계)
    REVIEW_NOT_ENDED(HttpStatus.CONFLICT, "모임이 끝난 뒤에 평가할 수 있어요"),
    REVIEW_EXPIRED(HttpStatus.CONFLICT, "평가 기간(모임 후 7일)이 지났어요"),
    REVIEW_ALREADY_DONE(HttpStatus.CONFLICT, "이미 평가를 마친 모임이에요"),
    REVIEW_TARGET_INVALID(HttpStatus.BAD_REQUEST, "이 모임에 함께한 사람만 고를 수 있어요"),
    RATING_SELF(HttpStatus.BAD_REQUEST, "본인은 평가할 수 없어요"),
    RATING_MISSING(HttpStatus.BAD_REQUEST, "왔다고 확인한 사람은 모두 한 번씩 평가해 주세요"),
    RATING_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "2점 이하는 이유를 목록에서 골라 주세요");

    private final HttpStatus status;
    private final String message;
}
