package com.chatssl.anytime.safety.dto;

import java.time.LocalDateTime;

import com.chatssl.anytime.user.dto.UserProfileResponse;

/** 차단 목록 한 줄. user는 GET /users/{id}와 같은 공개 프로필 */
public record BlockedUserResponse(
    UserProfileResponse user,
    LocalDateTime blockedAt
) {
}
