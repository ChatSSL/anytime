package com.chatssl.anytime.user.dto;

import java.util.Comparator;
import java.util.List;

import com.chatssl.anytime.domain.User;

/**
 * GET /api/me 응답. 본인에게만 주는 응답이라 이메일과 비공개 관심사도 들어 있다.
 * manner는 평균(현재 점수)만 내려보내고, 누가 몇 점을 줬는지는 보내지 않는다.
 */
public record MeResponse(
    Long id,
    String email,
    String school,
    String nick,
    String dept,
    boolean showDept,
    int avatar,
    List<InterestItem> interests,
    double manner,
    int completed,
    int sanction,
    /** 관심사를 하나 이상 고르면 프로필 만들기를 끝낸 것으로 본다. 처음 가입하면 false */
    boolean profileCompleted
) {

    public static MeResponse of(User user, String email) {
        List<InterestItem> interests = user.getInterests().stream()
            .sorted(Comparator.comparing(i -> i.getName()))
            .map(InterestItem::from)
            .toList();
        return new MeResponse(
            user.getId(), email, user.getSchool(), user.getNick(), user.getDept(), user.isShowDept(),
            user.getAvatar(), interests, user.getManner().doubleValue(), user.getCompleted(),
            user.getSanction(), !interests.isEmpty());
    }
}
