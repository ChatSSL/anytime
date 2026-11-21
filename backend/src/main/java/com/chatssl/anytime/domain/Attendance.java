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

/** confirmer가 user의 참석을 확인한 기록. 참석자 과반이 확인하면 모임 완료. */
@Entity
@Table(name = "attendance")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @EmbeddedId
    private AttendanceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roomId")
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("confirmedBy")
    @JoinColumn(name = "confirmed_by")
    private User confirmer;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Attendance(Room room, User user, User confirmer) {
        this.id = new AttendanceId(room.getId(), user.getId(), confirmer.getId());
        this.room = room;
        this.user = user;
        this.confirmer = confirmer;
    }
}
