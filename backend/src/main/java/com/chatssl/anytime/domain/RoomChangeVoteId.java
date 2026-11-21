package com.chatssl.anytime.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** room_change_votes 복합 키 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class RoomChangeVoteId implements Serializable {

    @Column(name = "change_id")
    private Long changeId;

    @Column(name = "user_id")
    private Long userId;
}
