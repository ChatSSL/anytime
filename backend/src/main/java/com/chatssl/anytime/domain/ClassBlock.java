package com.chatssl.anytime.domain;

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

/** 프론트 ClassBlock. 수업 시간 한 칸. */
@Entity
@Table(name = "class_blocks")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String name;

    @Column(nullable = false)
    private Integer day;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    public ClassBlock(User user, String name, int day, int startMin, int endMin) {
        this.user = user;
        this.name = name;
        this.day = day;
        this.startMin = startMin;
        this.endMin = endMin;
    }
}
