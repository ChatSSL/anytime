package com.chatssl.anytime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/** user_interests 한 줄. 공개한 관심사만 추천 이유와 AI 미션에 쓴다. */
@Embeddable
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(of = "name")
public class Interest {

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;
}
