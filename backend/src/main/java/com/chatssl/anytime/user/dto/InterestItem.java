package com.chatssl.anytime.user.dto;

import com.chatssl.anytime.domain.Interest;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관심사 하나. isPublic이 false면 다른 사람에게 안 보이고 추천 이유·AI 미션에도 안 쓴다.
 * 요청에서 isPublic을 빼면 공개(true)로 저장한다.
 */
public record InterestItem(
    @NotBlank @Size(max = 20, message = "관심사는 20자까지 쓸 수 있어요") String name,
    @JsonProperty("isPublic") Boolean isPublic
) {

    public static InterestItem from(Interest interest) {
        return new InterestItem(interest.getName(), interest.isPublic());
    }

    public boolean publicOrDefault() {
        return isPublic == null || isPublic;
    }
}
