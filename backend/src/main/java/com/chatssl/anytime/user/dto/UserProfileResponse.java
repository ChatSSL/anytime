package com.chatssl.anytime.user.dto;

import java.util.List;

import com.chatssl.anytime.domain.Interest;
import com.chatssl.anytime.domain.User;

/**
 * GET /api/users/{id} 응답. 다른 사람에게 보이는 것만 담는다.
 * 이메일·시간표·제재 상태는 넣지 않고, 학과는 공개한 경우만, 관심사는 공개한 것만 넣는다.
 */
public record UserProfileResponse(
    Long id,
    String nick,
    /** 비공개면 null */
    String dept,
    int avatar,
    List<String> interests,
    double manner,
    /** 완료한 모임 수 (프론트 User.meetups) */
    int completed
) {

    public static UserProfileResponse of(User user) {
        List<String> interests = user.getInterests().stream()
            .filter(Interest::isPublic)
            .map(Interest::getName)
            .sorted()
            .toList();
        return new UserProfileResponse(
            user.getId(), user.getNick(), user.isShowDept() ? user.getDept() : null, user.getAvatar(),
            interests, user.getManner().doubleValue(), user.getCompleted());
    }
}
