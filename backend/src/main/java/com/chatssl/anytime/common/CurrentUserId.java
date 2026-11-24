package com.chatssl.anytime.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러에서 현재 로그인한 사용자 id를 받는다.
 * 예: public RoomResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateRoomRequest req)
 *
 * 값은 Authorization: Bearer <액세스 토큰>의 사용자 id다 (7단계). 토큰이 없으면 401.
 * @AuthenticationPrincipal Long userId 로 받아도 같은 값이다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
