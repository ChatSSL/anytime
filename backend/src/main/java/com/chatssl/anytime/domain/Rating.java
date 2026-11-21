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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 상호 평가. 평가자는 상대에게 공개하지 않고, 크론이 모아서 매너 점수에 반영한다. */
@Entity
@Table(name = "ratings")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_id")
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id")
    private User target;

    /** 1~5 */
    @Column(nullable = false)
    private Integer score;

    /** 2점 이하면 필수 (서비스에서 검사) */
    @Column(length = 50)
    private String reason;

    @Column(length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 매너 점수에 반영된 시각. NULL이면 아직 대기 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    public Rating(Room room, User rater, User target, int score, String reason, String note) {
        this.room = room;
        this.rater = rater;
        this.target = target;
        this.score = score;
        this.reason = reason;
        this.note = note;
    }
}
