package com.chatssl.anytime.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;

class JwtProviderTest {

    private final JwtProvider provider = new JwtProvider("test-secret", 15, 14);

    @Test
    void 액세스_토큰에서_사용자_id를_꺼낸다() {
        String token = provider.createAccessToken(7L);
        assertThat(provider.parseAccessToken(token)).isEqualTo(7L);
    }

    @Test
    void 리프레시_토큰은_액세스_토큰으로_못_쓴다() {
        String refresh = provider.createRefreshToken(7L);
        assertThat(provider.parseRefreshToken(refresh)).isEqualTo(7L);
        assertCode(() -> provider.parseAccessToken(refresh), ErrorCode.AUTH_TOKEN_INVALID);
        assertCode(() -> provider.parseRefreshToken(provider.createAccessToken(7L)), ErrorCode.AUTH_REFRESH_INVALID);
    }

    @Test
    void 리프레시_토큰은_매번_다르다() {
        assertThat(provider.createRefreshToken(7L)).isNotEqualTo(provider.createRefreshToken(7L));
    }

    @Test
    void 다른_비밀키로_만든_토큰은_거절한다() {
        String other = new JwtProvider("other-secret", 15, 14).createAccessToken(7L);
        assertCode(() -> provider.parseAccessToken(other), ErrorCode.AUTH_TOKEN_INVALID);
        assertCode(() -> provider.parseAccessToken("not-a-jwt"), ErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void 만료된_토큰은_만료_코드를_준다() {
        String expired = new JwtProvider("test-secret", -1, 14).createAccessToken(7L);
        assertCode(() -> provider.parseAccessToken(expired), ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
