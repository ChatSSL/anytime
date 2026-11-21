package com.chatssl.anytime.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 이메일 인증번호. 발송 시 저장(같은 이메일이면 덮어쓰기), 검증 성공 시 삭제. 10분 만료, 5회 실패 시 재발송. */
@Entity
@Table(name = "email_codes")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailCode {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EmailCode(String emailHash, String codeHash, LocalDateTime expiresAt) {
        this.emailHash = emailHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }
}
