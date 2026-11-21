package com.chatssl.anytime.domain;

import java.math.BigDecimal;
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

/** 프론트 cancelLog. 매너 점수 차감 근거. */
@Entity
@Table(name = "cancel_log")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CancelLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false, length = 100)
    private String reason;

    /** 차감한 매너 점수. 0이면 차감 없음 */
    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal penalty = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CancelLog(User user, Room room, String reason, BigDecimal penalty) {
        this.user = user;
        this.room = room;
        this.reason = reason;
        this.penalty = penalty;
    }
}
