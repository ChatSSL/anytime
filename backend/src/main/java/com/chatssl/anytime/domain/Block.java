package com.chatssl.anytime.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 차단. 추천·채팅에서는 어느 쪽이 차단했든 서로 숨긴다. */
@Entity
@Table(name = "blocks")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @EmbeddedId
    private BlockId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockerId")
    @JoinColumn(name = "blocker_id")
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("blockedId")
    @JoinColumn(name = "blocked_id")
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Block(User blocker, User blocked) {
        this.id = new BlockId(blocker.getId(), blocked.getId());
        this.blocker = blocker;
        this.blocked = blocked;
    }
}
