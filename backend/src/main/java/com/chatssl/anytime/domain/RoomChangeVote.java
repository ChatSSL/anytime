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

/** 일정 변경에 대한 참여자별 동의/거절 */
@Entity
@Table(name = "room_change_votes")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomChangeVote {

    @EmbeddedId
    private RoomChangeVoteId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("changeId")
    @JoinColumn(name = "change_id")
    private RoomChange change;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean accepted;

    @CreationTimestamp
    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    public RoomChangeVote(RoomChange change, User user, boolean accepted) {
        this.id = new RoomChangeVoteId(change.getId(), user.getId());
        this.change = change;
        this.user = user;
        this.accepted = accepted;
    }
}
