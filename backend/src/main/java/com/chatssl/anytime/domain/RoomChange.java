package com.chatssl.anytime.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 방장의 일정 변경 제안 (프론트 pendingChange) */
@Entity
@Table(name = "room_changes")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "new_day", nullable = false)
    private Integer newDay;

    @Column(name = "new_start", nullable = false)
    private Integer newStart;

    @Column(name = "new_end", nullable = false)
    private Integer newEnd;

    @Column(name = "new_place", length = 50)
    private String newPlace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeStatus status = ChangeStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RoomChange(Room room, int newDay, int newStart, int newEnd, String newPlace) {
        this.room = room;
        this.newDay = newDay;
        this.newStart = newStart;
        this.newEnd = newEnd;
        this.newPlace = newPlace;
    }
}
