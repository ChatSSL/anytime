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

/** 1:1 요청 (프론트 requestDm(userId, slot, activity, message)) */
@Entity
@Table(name = "dm_requests")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_id")
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_id")
    private User toUser;

    @Column(nullable = false)
    private Integer day;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    @Column(length = 20)
    private String activity;

    @Column(length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DmStatus status = DmStatus.PENDING;

    /** 수락되어 만들어진 DM 채팅방. 같은 두 사람의 DM을 또 만들기 전에 이걸로 확인 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DmRequest(User from, User to, int day, int startMin, int endMin, String activity, String message) {
        this.fromUser = from;
        this.toUser = to;
        this.day = day;
        this.startMin = startMin;
        this.endMin = endMin;
        this.activity = activity;
        this.message = message;
    }
}
