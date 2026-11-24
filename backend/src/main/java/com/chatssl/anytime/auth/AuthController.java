package com.chatssl.anytime.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.auth.dto.LoginResponse;
import com.chatssl.anytime.auth.dto.RefreshRequest;
import com.chatssl.anytime.auth.dto.SendCodeRequest;
import com.chatssl.anytime.auth.dto.SendCodeResponse;
import com.chatssl.anytime.auth.dto.TokenResponse;
import com.chatssl.anytime.auth.dto.VerifyCodeRequest;
import com.chatssl.anytime.auth.dto.VerifyCodeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "학교 이메일 인증(5단계)과 로그인 토큰(7단계)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailAuthService emailAuthService;
    private final TokenService tokenService;

    @Operation(summary = "인증번호 보내기",
        description = "학교 이메일로 6자리 인증번호를 보낸다. 10분 유효, 1분 뒤 재발송 가능. "
            + "MAIL_USERNAME이 없으면 메일 대신 서버 로그에 인증번호가 찍힌다.")
    @PostMapping("/email")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return emailAuthService.sendCode(request.email());
    }

    @Operation(summary = "인증번호 확인 = 로그인",
        description = "맞으면 사용자 id와 토큰을 돌려준다. 처음 인증한 이메일이면 사용자를 만들고 newUser=true. "
            + "5번 틀리면 인증번호를 다시 받아야 한다. 액세스 토큰 15분, 리프레시 토큰 14일.")
    @PostMapping("/email/verify")
    public LoginResponse verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        VerifyCodeResponse verified = emailAuthService.verifyCode(request.email(), request.code());
        return LoginResponse.of(verified, tokenService.issue(verified.userId()));
    }

    @Operation(summary = "토큰 재발급",
        description = "액세스 토큰이 만료(AUTH_TOKEN_EXPIRED)되면 리프레시 토큰으로 새 토큰 한 쌍을 받는다. "
            + "쓴 리프레시 토큰은 다시 못 쓴다.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenService.refresh(request.refreshToken());
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기한다. 액세스 토큰은 프론트에서 지운다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshRequest request) {
        tokenService.logout(request == null ? null : request.refreshToken());
    }
}
