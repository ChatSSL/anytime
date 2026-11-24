package com.chatssl.anytime.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chatssl.anytime.common.ApiException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer <액세스 토큰> 을 읽어 SecurityContext에 사용자 id(Long)를 넣는다.
 * 토큰이 없거나 틀려도 여기서 막지 않는다. 막을지는 SecurityConfig의 경로 규칙이 정하고,
 * 막힐 때 JsonAuthEntryPoint가 이 필터가 남긴 에러 코드(만료/위조)를 응답한다.
 *
 * @Component로 등록하지 않는다 (서블릿 필터로 한 번 더 걸리지 않게). SecurityConfig에서 직접 만든다.
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 토큰 검사 실패 이유를 엔트리 포인트로 넘기는 요청 속성 이름 */
    public static final String ERROR_ATTRIBUTE = JwtAuthFilter.class.getName() + ".error";
    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            try {
                Long userId = jwtProvider.parseAccessToken(header.substring(BEARER.length()).trim());
                var auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ApiException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }
        chain.doFilter(request, response);
    }
}
