package com.chatssl.anytime.domain;

import java.time.LocalDateTime;

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

/** 채팅방 참여자와 마지막으로 읽은 시각 */
@Entity
@Table(name = "chat_members")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {

    @EmbeddedId
    private ChatMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("chatId")
    @JoinColumn(name = "chat_id")
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    /** 안 읽은 수는 이 시각 이후 메시지 수로 계산한다. */
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    public ChatMember(Chat chat, User user) {
        this.id = new ChatMemberId(chat.getId(), user.getId());
        this.chat = chat;
        this.user = user;
    }
}
