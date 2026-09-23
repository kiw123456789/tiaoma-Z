package com.tiaoma.controller;

import com.tiaoma.dto.AdminDtos.RoleUpdateRequest;
import com.tiaoma.model.User;
import com.tiaoma.repository.UserRepository;
import com.tiaoma.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ดูรายชื่อผู้สมัครสมาชิกทั้งหมด และเลื่อน/ถอดสิทธิ์แอดมิน — เข้าถึงได้เฉพาะ ADMIN เท่านั้น */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest req,
                                          Authentication auth) {
        User.Role newRole;
        try {
            newRole = User.Role.valueOf(req.role().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role ต้องเป็น ADMIN หรือ USER เท่านั้น");
        }

        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบผู้ใช้นี้"));

        User current = userRepository.findByEmail(AuthService.normalizeEmail(auth.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ไม่พบผู้ใช้ปัจจุบัน"));

        boolean demotingToUser = newRole == User.Role.USER;

        if (demotingToUser && target.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ไม่สามารถถอดสิทธิ์แอดมินของตัวเองได้ ให้แอดมินคนอื่นเป็นคนดำเนินการแทน");
        }
        if (demotingToUser && target.getRole() == User.Role.ADMIN && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ต้องมีแอดมินอย่างน้อย 1 คนในระบบเสมอ");
        }

        target.setRole(newRole);
        userRepository.save(target);
        // หมายเหตุ: ถ้าผู้ใช้ที่ถูกเปลี่ยน role กำลังล็อกอินอยู่ สิทธิ์ที่ session เดิมถืออยู่จะยังไม่เปลี่ยน
        // จนกว่าเขาจะออกจากระบบแล้วเข้าใหม่ (สิทธิ์ถูกฝังไว้ตอน login ไม่ได้เช็คสดทุกครั้ง)
        return view(target);
    }

    private Map<String, Object> view(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole().name());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }
}
