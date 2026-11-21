package com.chatssl.anytime.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** blocks 복합 키 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class BlockId implements Serializable {

    @Column(name = "blocker_id")
    private Long blockerId;

    @Column(name = "blocked_id")
    private Long blockedId;
}
