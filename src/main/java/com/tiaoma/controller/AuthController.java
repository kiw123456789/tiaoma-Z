package com.tiaoma.controller;

import com.tiaoma.dto.AuthDtos.ForgotPasswordRequest;
import com.tiaoma.dto.AuthDtos.LoginRequest;
import com.tiaoma.dto.AuthDtos.RegisterRequest;
import com.tiaoma.dto.AuthDtos.ResetPasswordRequest;
import com.tiaoma.model.User;
import com.tiaoma.repository.UserRepository;
import com.tiaoma.security.RateLimitFilter;
import com.tiaoma.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RateLimitFilter rateLimitFilter;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          RateLimitFilter rateLimitFilter) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.rateLimitFilter = rateLimitFilter;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req.name(), req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(userView(user));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            AuthService.normalizeEmail(req.email()), req.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "อีเมลหรือรหัสผ่านไม่ถูกต้อง หากยังไม่มีบัญชี กรุณาสมัครสมาชิกก่อน");
        }

        // ล็อกอินสำเร็จแล้ว ล้างตัวนับความพยายามล็อกอินของ IP นี้
        // คนที่พิมพ์รหัสผ่านผิดไปสองสามครั้งก่อนจะจำได้ จะได้ไม่โดนล็อกทิ้งไว้ยาว
        rateLimitFilter.resetLogin(request);

        // กัน session fixation: ถ้ามี session เดิมอยู่ให้เปลี่ยน id ก่อน
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ไม่พบผู้ใช้"));
        return userView(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    /** หน้าเว็บเรียกทุกครั้งที่โหลดหน้า เพื่อรู้ว่าใครล็อกอินอยู่ (ไม่ล็อกอิน = authenticated:false) */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return Map.of("authenticated", false);
        }
        return userRepository.findByEmail(auth.getName())
                .map(this::userView)
                .orElse(Map.of("authenticated", false));
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.requestPasswordReset(req.email());
        // ตอบเหมือนกันเสมอ ไม่บอกว่าอีเมลนี้มีในระบบหรือไม่
        return Map.of("message", "หากอีเมลนี้มีบัญชีอยู่ในระบบ เราได้ส่งลิงก์ตั้งรหัสผ่านใหม่ให้แล้ว (ลิงก์ใช้ได้ 30 นาที)");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.password());
        return Map.of("message", "ตั้งรหัสผ่านใหม่สำเร็จ กรุณาเข้าสู่ระบบด้วยรหัสผ่านใหม่");
    }

    private Map<String, Object> userView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("authenticated", true);
        view.put("id", user.getId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("role", user.getRole().name());
        return view;
    }
}
