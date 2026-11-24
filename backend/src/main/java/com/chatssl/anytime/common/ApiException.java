package com.chatssl.anytime.common;

import lombok.Getter;

/**
 * 서비스에서 예상한 실패는 이걸 던진다. GlobalExceptionHandler가 {code, message}로 바꿔 준다.
 * 예: throw new ApiException(ErrorCode.ROOM_NOT_FOUND);
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 문구 대신 다른 문구를 보여 줄 때 */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
