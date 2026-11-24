package com.chatssl.anytime.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatssl.anytime.auth.dto.SendCodeRequest;
import com.chatssl.anytime.auth.dto.SendCodeResponse;
import com.chatssl.anytime.auth.dto.VerifyCodeRequest;
import com.chatssl.anytime.auth.dto.VerifyCodeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "학교 이메일 인증 (5단계). 로그인 토큰은 7단계에서 붙는다")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailAuthService emailAuthService;

    @Operation(summary = "인증번호 보내기",
        description = "학교 이메일로 6자리 인증번호를 보낸다. 10분 유효, 1분 뒤 재발송 가능. "
            + "MAIL_USERNAME이 없으면 메일 대신 서버 로그에 인증번호가 찍힌다.")
    @PostMapping("/email")
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return emailAuthService.sendCode(request.email());
    }

    @Operation(summary = "인증번호 확인",
        description = "맞으면 사용자 id를 돌려준다. 처음 인증한 이메일이면 사용자를 만들고 newUser=true. "
            + "5번 틀리면 인증번호를 다시 받아야 한다.")
    @PostMapping("/email/verify")
    public VerifyCodeResponse verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        return emailAuthService.verifyCode(request.email(), request.code());
    }
}
