package com.tiaoma.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** โทเคนรีเซ็ตรหัสผ่าน เก็บเฉพาะค่าแฮช (SHA-256) ไม่เก็บโทเคนจริง */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public PasswordResetToken() {
    }

    public PasswordResetToken(String tokenHash, User user, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public User getUser() { return user; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isExpired() { return expiresAt.isBefore(LocalDateTime.now()); }
}
