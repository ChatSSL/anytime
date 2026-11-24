package com.chatssl.anytime.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.chatssl.anytime.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /** 만료된 토큰 정리. 로그인할 때마다 그 사용자 것만 지운다. */
    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt < :now")
    int deleteExpired(Long userId, LocalDateTime now);
}
