package com.tiaoma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * จำกัดจำนวนครั้งที่ยิง endpoint ที่อ่อนไหวต่อการโจมตี ต่อ IP ต่อช่วงเวลา
 *
 * <ul>
 *   <li>POST /api/auth/login — กันการเดารหัสผ่าน (brute force)</li>
 *   <li>POST /api/auth/forgot-password — กันการยิงอีเมลถล่มเหยื่อ (email bombing)</li>
 *   <li>POST /api/auth/register — กันการสมัครสมาชิกรัวๆ ด้วยบอท</li>
 * </ul>
 *
 * ใช้ in-memory counter (ConcurrentHashMap) เพราะระบบรันเครื่องเดียว
 * ถ้าขยายเป็นหลายเครื่อง (load balancer) ควรเปลี่ยนไปเก็บใน Redis แทน
 *
 * หมายเหตุสำคัญ: ตัวนี้ถูกเสียบเข้า filter chain ของ Spring Security เอง (ดู SecurityConfig)
 * จึงต้องปิดการลงทะเบียนอัตโนมัติของ Spring Boot ด้วย (ดู RateLimitFilterRegistration)
 * ไม่งั้นทุกคำขอจะถูกนับสองครั้ง
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** ล้าง counter ที่หมดอายุทุกๆ กี่คำขอ (กัน map โตไม่จำกัด) */
    private static final int CLEANUP_EVERY = 500;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    private final int loginMax;
    private final Duration loginWindow;
    private final int forgotMax;
    private final Duration forgotWindow;

    public RateLimitFilter(
            @Value("${app.ratelimit.login.max-attempts:8}") int loginMax,
            @Value("${app.ratelimit.login.window-minutes:15}") int loginWindowMinutes,
            @Value("${app.ratelimit.forgot.max-attempts:4}") int forgotMax,
            @Value("${app.ratelimit.forgot.window-minutes:60}") int forgotWindowMinutes) {
        this.loginMax = loginMax;
        this.loginWindow = Duration.ofMinutes(loginWindowMinutes);
        this.forgotMax = forgotMax;
        this.forgotWindow = Duration.ofMinutes(forgotWindowMinutes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Rule rule = ruleFor(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        cleanupOccasionally();

        String key = rule.name() + "|" + clientIp(request);
        Counter counter = counters.compute(key, (k, existing) ->
                (existing == null || existing.isExpired()) ? new Counter(rule.window()) : existing);

        int used = counter.increment();
        if (used > rule.max()) {
            long secondsLeft = counter.secondsLeft();
            log.warn("Rate limit: {} จาก IP {} (ครั้งที่ {} เกินโควตา {})",
                    rule.name(), clientIp(request), used, rule.max());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(secondsLeft));
            long minutesLeft = Math.max(1, secondsLeft / 60);
            response.getWriter().write(String.format(
                    "{\"message\":\"%s กรุณารออีกประมาณ %d นาทีแล้วลองใหม่อีกครั้ง\"}",
                    rule.message(), minutesLeft));
            return;
        }

        chain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) {
            return new Rule("login", loginMax, loginWindow,
                    "คุณพยายามเข้าสู่ระบบบ่อยเกินไป เพื่อความปลอดภัยของบัญชี");
        }
        if ("/api/auth/forgot-password".equals(path)) {
            return new Rule("forgot", forgotMax, forgotWindow,
                    "คุณขอลิงก์ตั้งรหัสผ่านใหม่บ่อยเกินไป");
        }
        if ("/api/auth/register".equals(path)) {
            return new Rule("register", forgotMax, forgotWindow,
                    "คุณสมัครสมาชิกบ่อยเกินไป");
        }
        return null;
    }

    /** เคารพ X-Forwarded-For เผื่อรันหลัง reverse proxy / load balancer */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupOccasionally() {
        if (requestsSinceCleanup.incrementAndGet() < CLEANUP_EVERY) {
            return;
        }
        requestsSinceCleanup.set(0);
        counters.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /** ล้าง counter ของ IP หลังล็อกอินสำเร็จ (ให้คนที่พิมพ์ผิดไม่กี่ครั้งไม่โดนแบนต่อ) */
    public void resetLogin(HttpServletRequest request) {
        counters.remove("login|" + clientIp(request));
    }

    private record Rule(String name, int max, Duration window, String message) {
    }

    private static final class Counter {
        private final Instant expiresAt;
        private final AtomicInteger hits = new AtomicInteger();

        Counter(Duration window) {
            this.expiresAt = Instant.now().plus(window);
        }

        int increment() {
            return hits.incrementAndGet();
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        long secondsLeft() {
            return Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        }
    }
}
