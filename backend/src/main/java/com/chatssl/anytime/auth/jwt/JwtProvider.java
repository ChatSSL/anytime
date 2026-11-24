package com.chatssl.anytime.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 만들기·검사 (HS256).
 * - 액세스 토큰: 15분. 매 요청 Authorization: Bearer 헤더로 온다
 * - 리프레시 토큰: 14일. /api/auth/refresh 에서만 쓰고, DB(refresh_tokens)에 해시가 있어야 유효
 * 두 토큰은 typ 클레임으로 구분해서 서로 바꿔 쓰지 못하게 한다.
 */
@Component
public class JwtProvider {

    private static final String TYPE = "typ";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-minutes}") long accessMinutes,
                       @Value("${app.jwt.refresh-days}") long refreshDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 환경 변수를 설정해 주세요");
        }
        // 비밀 문자열 길이와 상관없이 HS256에 맞는 32바이트 키로 만든다
        this.key = Keys.hmacShaKeyFor(sha256(secret.getBytes(StandardCharsets.UTF_8)));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    public String createAccessToken(Long userId) {
        return build(userId, ACCESS, accessTtl, null);
    }

    /** 같은 순간에 두 번 만들어도 다르게 나오도록 jti를 넣는다. */
    public String createRefreshToken(Long userId) {
        return build(userId, REFRESH, refreshTtl, UUID.randomUUID().toString());
    }

    /** 액세스 토큰이면 사용자 id. 만료면 AUTH_TOKEN_EXPIRED, 그 밖의 문제는 AUTH_TOKEN_INVALID */
    public Long parseAccessToken(String token) {
        return parse(token, ACCESS, ErrorCode.AUTH_TOKEN_EXPIRED, ErrorCode.AUTH_TOKEN_INVALID);
    }

    public Long parseRefreshToken(String token) {
        return parse(token, REFRESH, ErrorCode.AUTH_REFRESH_INVALID, ErrorCode.AUTH_REFRESH_INVALID);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    /** DB에 저장할 리프레시 토큰 해시 (64자 hex) */
    public static String hash(String token) {
        return java.util.HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
    }

    private String build(Long userId, String type, Duration ttl, String id) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(TYPE, type)
            .id(id)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
    }

    private Long parse(String token, String expectedType, ErrorCode expired, ErrorCode invalid) {
        if (token == null || token.isBlank()) {
            throw new ApiException(invalid);
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(TYPE, String.class))) {
                throw new ApiException(invalid);
            }
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new ApiException(expired);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(invalid);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
