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

/** 프론트 Room.members. 모임 참여 기록. */
@Entity
@Table(name = "room_members")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {

    @EmbeddedId
    private RoomMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roomId")
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** NULL이면 참여 중. 다시 합류하면 NULL로 되돌린다. */
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "leave_reason", length = 100)
    private String leaveReason;

    public RoomMember(Room room, User user) {
        this.id = new RoomMemberId(room.getId(), user.getId());
        this.room = room;
        this.user = user;
    }

    public boolean isActive() {
        return leftAt == null;
    }
}
