package com.tiaoma.service;

import com.tiaoma.model.PasswordResetToken;
import com.tiaoma.model.User;
import com.tiaoma.repository.PasswordResetTokenRepository;
import com.tiaoma.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** เทสต์ flow สมัครสมาชิกและรีเซ็ตรหัสผ่าน ซึ่งเป็นส่วนที่อ่อนไหวที่สุดของระบบ */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("normalizeEmail ตัดช่องว่างและแปลงเป็นตัวพิมพ์เล็ก")
    void normalizeEmail_trimsAndLowercases() {
        assertThat(AuthService.normalizeEmail("  TesT@Example.COM  ")).isEqualTo("test@example.com");
        assertThat(AuthService.normalizeEmail(null)).isEmpty();
    }

    @Test
    @DisplayName("สมัครสมาชิกสำเร็จ และรหัสผ่านถูกเข้ารหัสไว้ ไม่ได้เก็บเป็นข้อความธรรมดา")
    void register_hashesPassword() {
        User user = authService.register("สมชาย ใจดี", "Somchai@Example.com", "password123");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("somchai@example.com");
        assertThat(user.getName()).isEqualTo("สมชาย ใจดี");
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPasswordHash())).isTrue();
        assertThat(user.getRole()).isEqualTo(User.Role.USER);
    }

    @Test
    @DisplayName("สมัครด้วยอีเมลซ้ำ (ต่างตัวพิมพ์) ต้องถูกปฏิเสธ")
    void register_duplicateEmail_rejected() {
        authService.register("คนแรก", "dup@example.com", "password123");

        assertThatThrownBy(() -> authService.register("คนที่สอง", "DUP@example.com", "password456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("อีเมลนี้มีผู้ใช้งานสมัครไว้แล้ว");

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("ขอลิงก์รีเซ็ตด้วยอีเมลที่ไม่มีในระบบ ต้องไม่สร้างโทเคนและไม่ throw error")
    void requestPasswordReset_unknownEmail_silentlyIgnored() {
        authService.requestPasswordReset("nobody@example.com");
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("ขอลิงก์รีเซ็ตสองครั้ง โทเคนเก่าต้องถูกยกเลิก เหลือใบเดียว")
    void requestPasswordReset_twice_keepsOnlyLatestToken() {
        authService.register("ทดสอบ", "reset@example.com", "password123");

        authService.requestPasswordReset("reset@example.com");
        assertThat(tokenRepository.count()).isEqualTo(1);

        authService.requestPasswordReset("reset@example.com");
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("โทเคนถูกเก็บเป็นค่า hash ไม่ใช่ค่าดิบ")
    void resetToken_isStoredHashed() {
        authService.register("ทดสอบ", "hash@example.com", "password123");
        authService.requestPasswordReset("hash@example.com");

        PasswordResetToken token = tokenRepository.findAll().get(0);
        // SHA-256 hex = 64 ตัวอักษร
        assertThat(token.getTokenHash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("ใช้โทเคนที่ไม่มีอยู่จริง ต้องได้ข้อความว่าลิงก์ไม่ถูกต้อง")
    void resetPassword_invalidToken_rejected() {
        assertThatThrownBy(() -> authService.resetPassword("ไม่มีโทเคนนี้", "newpassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ลิงก์ตั้งรหัสผ่านไม่ถูกต้องหรือหมดอายุแล้ว");
    }

    @Test
    @DisplayName("โทเคนที่หมดอายุแล้วใช้ไม่ได้ และถูกลบทิ้ง")
    void resetPassword_expiredToken_rejectedAndDeleted() {
        User user = authService.register("ทดสอบ", "expired@example.com", "password123");

        // สร้างโทเคนที่หมดอายุไปแล้วด้วยมือ (hash ของคำว่า "raw-token")
        String rawToken = "raw-token";
        String hash = sha256(rawToken);
        tokenRepository.save(new PasswordResetToken(hash, user, LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "newpassword123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("หมดอายุ");

        assertThat(tokenRepository.findByTokenHash(hash)).isEmpty();
    }

    @Test
    @DisplayName("รีเซ็ตรหัสผ่านสำเร็จ รหัสใหม่ใช้ได้ รหัสเก่าใช้ไม่ได้ และโทเคนถูกลบ (ใช้ซ้ำไม่ได้)")
    void resetPassword_success_invalidatesTokenAndOldPassword() {
        User user = authService.register("ทดสอบ", "ok@example.com", "oldpassword123");

        String rawToken = "valid-token";
        tokenRepository.save(new PasswordResetToken(sha256(rawToken), user,
                LocalDateTime.now().plusMinutes(30)));

        authService.resetPassword(rawToken, "brandnewpass456");

        User reloaded = userRepository.findByEmail("ok@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("brandnewpass456", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("oldpassword123", reloaded.getPasswordHash())).isFalse();

        // โทเคนใช้ได้ครั้งเดียว
        assertThat(tokenRepository.findByTokenHash(sha256(rawToken))).isEmpty();
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
