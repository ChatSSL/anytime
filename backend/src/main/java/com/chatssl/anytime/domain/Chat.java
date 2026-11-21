package com.chatssl.anytime.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 프론트 Chat. ROOM이면 room이 있고, DM이면 chat_members 두 명으로 상대를 찾는다. */
@Entity
@Table(name = "chats")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatKind kind;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", unique = true)
    private Room room;

    /** AI가 만든 대화 주제·미션 (A단계) */
    @JdbcTypeCode(SqlTypes.JSON)
    private AiMission mission;

    @Column(name = "mission_done", nullable = false)
    private boolean missionDone = false;

    /** 오픈채팅 링크 대체 */
    private String link;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Chat forRoom(Room room) {
        Chat chat = new Chat();
        chat.kind = ChatKind.ROOM;
        chat.room = room;
        return chat;
    }

    public static Chat dm() {
        Chat chat = new Chat();
        chat.kind = ChatKind.DM;
        return chat;
    }
}
