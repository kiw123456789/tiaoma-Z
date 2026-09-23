package com.tiaoma.controller;

import com.tiaoma.dto.AccountDtos.ChangePasswordRequest;
import com.tiaoma.dto.AccountDtos.DeleteAccountRequest;
import com.tiaoma.dto.AccountDtos.UpdateProfileRequest;
import com.tiaoma.model.User;
import com.tiaoma.repository.LikeRepository;
import com.tiaoma.repository.PasswordResetTokenRepository;
import com.tiaoma.repository.ReviewRepository;
import com.tiaoma.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * จัดการบัญชีของผู้ใช้ที่ล็อกอินอยู่ — แก้ชื่อ, เปลี่ยนรหัสผ่าน, ลบบัญชี
 * (SecurityConfig บังคับให้ทุก endpoint ในนี้ต้องล็อกอินก่อน)
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LikeRepository likeRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordResetTokenRepository tokenRepository;

    public AccountController(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             LikeRepository likeRepository,
                             ReviewRepository reviewRepository,
                             PasswordResetTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.likeRepository = likeRepository;
        this.reviewRepository = reviewRepository;
        this.tokenRepository = tokenRepository;
    }

    /** ข้อมูลโปรไฟล์ + สถิติการใช้งาน */
    @GetMapping
    public Map<String, Object> profile(Authentication auth) {
        User user = currentUser(auth);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("name", user.getName());
        m.put("email", user.getEmail());
        m.put("role", user.getRole().name());
        m.put("createdAt", user.getCreatedAt());
        m.put("likeCount", likeRepository.countByIdUserId(user.getId()));
        return m;
    }

    /** แก้ไขชื่อที่แสดง */
    @PutMapping("/profile")
    @Transactional
    public Map<String, Object> updateProfile(@Valid @RequestBody UpdateProfileRequest req, Authentication auth) {
        User user = currentUser(auth);
        user.setName(req.name().trim());
        userRepository.save(user);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", user.getName());
        m.put("message", "บันทึกชื่อใหม่เรียบร้อยแล้ว");
        return m;
    }

    /** เปลี่ยนรหัสผ่านขณะที่ยังล็อกอินอยู่ (ต้องยืนยันรหัสผ่านเดิม) */
    @PostMapping("/change-password")
    @Transactional
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                              Authentication auth,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        User user = currentUser(auth);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "รหัสผ่านปัจจุบันไม่ถูกต้อง");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "รหัสผ่านใหม่ต้องไม่ซ้ำกับรหัสผ่านเดิม");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // ยกเลิกลิงก์รีเซ็ตรหัสผ่านที่ค้างอยู่ทั้งหมด
        tokenRepository.deleteByUser(user);

        // ออกจากระบบทุกอุปกรณ์ เพื่อความปลอดภัย แล้วให้ล็อกอินใหม่
        new SecurityContextLogoutHandler().logout(request, response, auth);

        return Map.of("message", "เปลี่ยนรหัสผ่านสำเร็จ กรุณาเข้าสู่ระบบใหม่ด้วยรหัสผ่านใหม่");
    }

    /** ลบบัญชีถาวร พร้อมข้อมูลที่เที่ยวโปรดและรีวิวทั้งหมด */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req,
                                              Authentication auth,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        User user = currentUser(auth);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "รหัสผ่านไม่ถูกต้อง");
        }
        // กันไม่ให้แอดมินคนสุดท้ายลบตัวเองทิ้ง จนไม่เหลือใครดูแลระบบ
        if (user.getRole() == User.Role.ADMIN && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "คุณเป็นผู้ดูแลระบบคนสุดท้าย กรุณาตั้งแอดมินคนอื่นก่อนลบบัญชีนี้");
        }

        likeRepository.deleteByIdUserId(user.getId());
        reviewRepository.deleteByUserId(user.getId());
        tokenRepository.deleteByUser(user);
        userRepository.delete(user);

        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบก่อน");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบใหม่"));
    }
}
