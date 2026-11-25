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

    // 모임
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없어요");

    private final HttpStatus status;
    private final String message;
}
