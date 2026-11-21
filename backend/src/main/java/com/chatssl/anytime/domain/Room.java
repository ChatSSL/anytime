package com.chatssl.anytime.domain;

import java.time.LocalDate;
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

/** 프론트 Room. 참여자는 room_members에 있다. */
@Entity
@Table(name = "rooms")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @Column(nullable = false, length = 50)
    private String title;

    /** ACTIVITIES 중 하나. 모임 목적 표시에도 쓴다. */
    @Column(nullable = false, length = 20)
    private String activity;

    @Column(nullable = false)
    private Integer day;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    /** 실제 모이는 날짜. 만들 때 서버가 day로부터 가장 가까운 그 요일 날짜를 넣는다. */
    @Column(name = "meet_date", nullable = false)
    private LocalDate meetDate;

    @Column(nullable = false, length = 50)
    private String place;

    @Column(nullable = false)
    private Integer cap;

    @Column(length = 200)
    private String topic;

    /** 운영팀이 연 모임 */
    @Column(nullable = false)
    private boolean official = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Room(User host, String title, String activity, int day, int startMin, int endMin,
                LocalDate meetDate, String place, int cap, String topic) {
        this.host = host;
        this.title = title;
        this.activity = activity;
        this.day = day;
        this.startMin = startMin;
        this.endMin = endMin;
        this.meetDate = meetDate;
        this.place = place;
        this.cap = cap;
        this.topic = topic;
    }
}
