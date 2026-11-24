package com.chatssl.anytime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.chatssl.anytime.auth.jwt.JsonAuthEntryPoint;
import com.chatssl.anytime.auth.jwt.JwtAuthFilter;
import com.chatssl.anytime.auth.jwt.JwtProvider;

/**
 * JWT 설정 (7단계).
 * - 세션·폼 로그인·CSRF 없음. 매 요청 Authorization: Bearer <액세스 토큰>
 * - /api/** 는 로그인 필요. 아래 PUBLIC 경로만 토큰 없이 열린다
 * - /api 밖(나중에 붙는 프론트 빌드 파일, Swagger)은 열어 둔다
 * 새 공개 API가 필요하면 PUBLIC_API에 추가한다 (담당: 2번).
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_API = {
        "/api/health",
        "/api/auth/email",
        "/api/auth/email/verify",
        "/api/auth/refresh",
        "/api/auth/logout",
        "/api/auth/dev-login",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider,
                                           JsonAuthEntryPoint entryPoint) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_API).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(entryPoint))
            .addFilterBefore(new JwtAuthFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
