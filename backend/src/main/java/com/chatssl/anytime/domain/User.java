package com.chatssl.anytime.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 프론트 User + Profile. 실명·학번·비밀번호는 저장하지 않는다. */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학교 이메일 HMAC-SHA256 (서버 비밀키 EMAIL_HASH_KEY). 로그인 조회와 중복 검사에 쓴다. */
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    /** 학교 이메일 암호문. 5단계에서 메일 보낼 때만 복호화한다. */
    @Column(name = "email_enc", nullable = false)
    private String emailEnc;

    @Column(nullable = false, length = 50)
    private String school;

    @Column(nullable = false, unique = true, length = 20)
    private String nick;

    @Column(length = 50)
    private String dept;

    @Column(name = "show_dept", nullable = false)
    private boolean showDept = true;

    @Column(nullable = false)
    private Integer avatar = 0;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal manner = new BigDecimal("36.5");

    /** 완료한 모임 수 (프론트 User.meetups) */
    @Column(nullable = false)
    private Integer completed = 0;

    /** 0 없음, 1 주의, 2 일시 정지, 3 영구 정지 */
    @Column(nullable = false)
    private Integer sanction = 0;

    /** 알림 종류별 on/off, ads, quiet, premium */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> settings;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    private Set<Interest> interests = new HashSet<>();

    /** 만나고 싶은 공강 (프론트 wanted) */
    @ElementCollection
    @CollectionTable(name = "wanted_slots", joinColumns = @JoinColumn(name = "user_id"))
    private Set<Slot> wantedSlots = new HashSet<>();

    public User(String emailHash, String emailEnc, String school, String nick) {
        this.emailHash = emailHash;
        this.emailEnc = emailEnc;
        this.school = school;
        this.nick = nick;
    }
}
