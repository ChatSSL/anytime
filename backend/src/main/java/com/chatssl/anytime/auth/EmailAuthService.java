package com.chatssl.anytime.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatssl.anytime.auth.dto.SendCodeResponse;
import com.chatssl.anytime.auth.dto.VerifyCodeResponse;
import com.chatssl.anytime.common.ApiException;
import com.chatssl.anytime.common.ErrorCode;
import com.chatssl.anytime.domain.EmailCode;
import com.chatssl.anytime.domain.User;
import com.chatssl.anytime.repository.EmailCodeRepository;
import com.chatssl.anytime.repository.UserRepository;

/**
 * 학교 이메일 인증 (5단계).
 * 1) send: 학교 도메인 검사 -> 6자리 코드 생성 -> 해시로 저장 -> 메일 발송
 * 2) verify: 만료·실패 횟수 검사 -> 맞으면 코드 삭제, 처음이면 사용자 생성
 * JWT 발급은 7단계(2번)에서 AuthController.verify 뒤에 붙인다.
 */
@Service
public class EmailAuthService {

    static final int VALID_MINUTES = 10;
    static final int MAX_FAILS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /** 학교 이메일 도메인 -> 학교 이름 */
    private static final Map<String, String> SCHOOLS = Map.of("dankook.ac.kr", "단국대학교");

    private final EmailCodeRepository emailCodeRepository;
    private final UserRepository userRepository;
    private final EmailCrypto crypto;
    private final MailCodeSender mailCodeSender;
    private final List<String> allowedDomains;
    private final SecureRandom random = new SecureRandom();

    public EmailAuthService(EmailCodeRepository emailCodeRepository, UserRepository userRepository,
                            EmailCrypto crypto, MailCodeSender mailCodeSender,
                            @Value("${app.auth.allowed-domains}") List<String> allowedDomains) {
        this.emailCodeRepository = emailCodeRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.mailCodeSender = mailCodeSender;
        this.allowedDomains = allowedDomains;
    }

    @Transactional
    public SendCodeResponse sendCode(String rawEmail) {
        String email = EmailCrypto.normalize(rawEmail);
        checkDomain(email);
        String emailHash = crypto.hash(email);
        LocalDateTime now = LocalDateTime.now();

        Optional<EmailCode> existing = emailCodeRepository.findById(emailHash);
        if (existing.isPresent() && existing.get().getCreatedAt() != null
                && existing.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_RESEND_TOO_SOON);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeHash = crypto.codeHash(emailHash, code);
        LocalDateTime expiresAt = now.plusMinutes(VALID_MINUTES);

        EmailCode emailCode = existing.orElseGet(() -> new EmailCode(emailHash, codeHash, expiresAt));
        emailCode.setCodeHash(codeHash);
        emailCode.setFailCount(0);
        emailCode.setExpiresAt(expiresAt);
        emailCode.setCreatedAt(now);
        emailCodeRepository.save(emailCode);

        // 발송이 실패하면 예외로 트랜잭션이 롤백되어 코드도 저장되지 않는다.
        mailCodeSender.send(email, code, VALID_MINUTES);
        return new SendCodeResponse(VALID_MINUTES * 60, (int) RESEND_COOLDOWN.toSeconds());
    }

    /** 실패 횟수 증가는 예외가 나도 저장되어야 하므로 ApiException에서는 롤백하지 않는다. */
    @Transactional(noRollbackFor = ApiException.class)
    public VerifyCodeResponse verifyCode(String rawEmail, String code) {
        String email = EmailCrypto.normalize(rawEmail);
        String emailHash = crypto.hash(email);
        LocalDateTime now = LocalDateTime.now();

        EmailCode emailCode = emailCodeRepository.findById(emailHash)
            .orElseThrow(() -> new ApiException(ErrorCode.AUTH_CODE_NOT_FOUND));
        if (emailCode.getFailCount() >= MAX_FAILS) {
            throw new ApiException(ErrorCode.AUTH_CODE_LOCKED);
        }
        if (emailCode.isExpired(now)) {
            emailCodeRepository.delete(emailCode);
            throw new ApiException(ErrorCode.AUTH_CODE_EXPIRED);
        }
        if (!crypto.matches(emailCode.getCodeHash(), crypto.codeHash(emailHash, code))) {
            int fails = emailCode.getFailCount() + 1;
            emailCode.setFailCount(fails);
            if (fails >= MAX_FAILS) {
                throw new ApiException(ErrorCode.AUTH_CODE_LOCKED);
            }
            throw new ApiException(ErrorCode.AUTH_CODE_MISMATCH,
                "인증번호가 맞지 않아요 (" + (MAX_FAILS - fails) + "번 남음)");
        }
        emailCodeRepository.delete(emailCode);

        Optional<User> found = userRepository.findByEmailHash(emailHash);
        if (found.isPresent()) {
            User user = found.get();
            if (user.getSanction() >= 2) {
                throw new ApiException(ErrorCode.USER_SUSPENDED);
            }
            return new VerifyCodeResponse(user.getId(), false);
        }

        User user = new User(emailHash, crypto.encrypt(email), schoolOf(email), temporaryNick());
        user.setVerifiedAt(now);
        userRepository.save(user);
        return new VerifyCodeResponse(user.getId(), true);
    }

    private void checkDomain(String email) {
        int at = email.lastIndexOf('@');
        String domain = at < 0 ? "" : email.substring(at + 1);
        if (!allowedDomains.contains(domain)) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_DOMAIN);
        }
    }

    private String schoolOf(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);
        return SCHOOLS.getOrDefault(domain, domain);
    }

    /** 프로필 설정(8단계) 전까지 쓰는 임시 닉네임. 겹치지 않게 뽑는다. */
    private String temporaryNick() {
        for (int i = 0; i < 20; i++) {
            String nick = "새친구" + String.format("%04d", random.nextInt(10_000));
            if (!userRepository.existsByNick(nick)) {
                return nick;
            }
        }
        return "새친구" + System.currentTimeMillis() % 1_000_000;
    }
}
