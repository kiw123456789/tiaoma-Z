package com.tiaoma.service;

import com.tiaoma.model.PasswordResetToken;
import com.tiaoma.model.User;
import com.tiaoma.repository.PasswordResetTokenRepository;
import com.tiaoma.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final String EMAIL_TAKEN = "อีเมลนี้มีผู้ใช้งานสมัครไว้แล้ว กรุณาเข้าสู่ระบบแทน";
    private static final String BAD_TOKEN = "ลิงก์ตั้งรหัสผ่านไม่ถูกต้องหรือหมดอายุแล้ว กรุณาขอลิงก์ใหม่";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final String publicBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       MailService mailService,
                       @Value("${app.public-base-url}") String publicBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User register(String name, String email, String password) {
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_TAKEN);
        }
        try {
            return userRepository.saveAndFlush(new User(name.trim(), normalized, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // สมัครพร้อมกันสองครั้งด้วยอีเมลเดียวกัน
            throw new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_TAKEN);
        }
    }

    /** สร้างโทเคนและส่งลิงก์ให้ทางอีเมล ถ้าไม่พบอีเมลนี้จะไม่ทำอะไร (ผู้เรียกตอบข้อความเดียวกันเสมอ) */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            tokenRepository.deleteByUser(user);   // ยกเลิกลิงก์เก่าทั้งหมด
            tokenRepository.flush();

            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            tokenRepository.save(new PasswordResetToken(sha256(rawToken), user,
                    LocalDateTime.now().plus(RESET_TOKEN_TTL)));

            mailService.sendPasswordReset(user.getEmail(), user.getName(),
                    publicBaseUrl + "/reset-password.html?token=" + rawToken);
        });
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_TOKEN));

        if (token.isExpired()) {
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_TOKEN);
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByUser(user);   // ใช้ได้ครั้งเดียว
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
