package com.chatssl.anytime.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이메일 보호.
 * - hash: HMAC-SHA256(EMAIL_HASH_KEY). 로그인 조회와 중복 검사용. 같은 이메일이면 항상 같은 값
 * - encrypt/decrypt: AES-256-GCM(EMAIL_ENC_KEY). 메일을 보낼 때만 복호화
 * 키 문자열은 SHA-256으로 32바이트 키로 바꿔 쓰므로 길이 제한이 없다.
 */
@Component
public class EmailCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec hashKey;
    private final SecretKeySpec encKey;
    private final SecureRandom random = new SecureRandom();

    public EmailCrypto(@Value("${app.email.hash-key}") String hashKey,
                       @Value("${app.email.enc-key}") String encKey) {
        if (hashKey == null || hashKey.isBlank() || encKey == null || encKey.isBlank()) {
            throw new IllegalStateException("EMAIL_HASH_KEY, EMAIL_ENC_KEY 환경 변수를 설정해 주세요");
        }
        this.hashKey = new SecretKeySpec(sha256(hashKey), "HmacSHA256");
        this.encKey = new SecretKeySpec(sha256(encKey), "AES");
    }

    /** 대소문자·앞뒤 공백을 정리한 이메일 */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 이메일 HMAC (64자 hex). users.email_hash, email_codes.email_hash */
    public String hash(String email) {
        return hmac(normalize(email));
    }

    /** 인증번호 HMAC. 이메일 해시와 묶어서 다른 이메일의 코드와 섞이지 않게 한다. */
    public String codeHash(String emailHash, String code) {
        return hmac(emailHash + ":" + code);
    }

    /** 두 해시를 시간 차 없이 비교 */
    public boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
            actualHash.getBytes(StandardCharsets.UTF_8));
    }

    /** AES-GCM 암호문 (base64: iv 12바이트 + 암호문). users.email_enc */
    public String encrypt(String email) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalize(email).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 암호화 실패", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("이메일 복호화 실패 (키가 바뀌었거나 시드 데이터)", e);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
