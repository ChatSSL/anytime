package com.chatssl.anytime.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 인증번호 메일 발송.
 * MAIL_USERNAME이 비어 있으면(로컬 개발) 메일 대신 서버 로그에 인증번호를 찍는다.
 */
@Slf4j
@Component
public class MailCodeSender {

    private final JavaMailSender mailSender;
    private final String from;

    public MailCodeSender(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String code, int validMinutes) {
        if (from == null || from.isBlank()) {
            log.warn("[개발 모드] 메일 설정이 없어 로그로 대신 보냅니다. {} 인증번호: {}", to, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[Anytime] 학교 이메일 인증번호");
        message.setText("인증번호는 " + code + " 입니다.\n"
            + validMinutes + "분 안에 입력해 주세요. 요청하지 않았다면 이 메일은 무시하셔도 됩니다.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("인증 메일 발송 실패: {}", e.getMessage());
            throw new ApiException(ErrorCode.AUTH_SEND_FAILED);
        }
    }
}
