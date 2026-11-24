package com.chatssl.anytime.auth;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.auth.dto.TokenResponse;
import com.chatssl.anytime.auth.jwt.JwtProvider;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.RefreshToken;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.RefreshTokenRepository;
import com.chatssl.anytime.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 토큰 발급·재발급·폐기 (7단계).
 * 리프레시 토큰은 한 번 쓰면 지우고 새로 준다(회전). 그래서 훔친 토큰을 다시 쓰면 실패한다.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /** 이메일 인증이 끝난 사용자에게 토큰 한 쌍을 준다. */
    @Transactional
    public TokenResponse issue(Long userId) {
        refreshTokenRepository.deleteExpired(userId, LocalDateTime.now());
        String access = jwtProvider.createAccessToken(userId);
        String refresh = jwtProvider.createRefreshToken(userId);
        refreshTokenRepository.save(new RefreshToken(JwtProvider.hash(refresh), userId,
            LocalDateTime.now().plus(jwtProvider.getRefreshTtl())));
        return new TokenResponse(access, refresh, jwtProvider.getAccessTtl().toSeconds());
    }

    /** 리프레시 토큰으로 새 토큰 한 쌍. 쓴 리프레시 토큰은 지운다. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Long userId = jwtProvider.parseRefreshToken(refreshToken);
        RefreshToken saved = refreshTokenRepository.findById(JwtProvider.hash(refreshToken))
            .filter(t -> t.getUserId().equals(userId))
            .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        refreshTokenRepository.delete(saved);
        if (saved.isExpired(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        checkActive(userId);
        return issue(userId);
    }

    /** 로그아웃: 그 리프레시 토큰만 지운다. 이미 없거나 틀린 토큰이어도 성공으로 본다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteById(JwtProvider.hash(refreshToken));
        }
    }

    /** 탈퇴했거나 정지된 사용자는 재발급하지 않는다. */
    public void checkActive(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (user.getSanction() >= 2) {
            throw new ApiException(ErrorCode.USER_SUSPENDED);
        }
    }
}
