package com.chatssl.anytime.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러에서 현재 로그인한 사용자 id를 받는다.
 * 예: public RoomResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateRoomRequest req)
 *
 * 7단계(JWT) 전에는 X-User-Id 헤더 값, 헤더가 없으면 1(데모유저)을 쓴다.
 * 7단계에서 2번이 CurrentUserIdResolver만 JWT 기준으로 바꾸면 컨트롤러는 고칠 필요 없다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
