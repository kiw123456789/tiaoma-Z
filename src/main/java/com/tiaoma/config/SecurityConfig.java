package com.tiaoma.config;

import com.tiaoma.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
public class SecurityConfig {

    private final boolean h2ConsoleEnabled;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
                          RateLimitFilter rateLimitFilter) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // หน้าเว็บและ API อยู่ origin เดียวกัน ใช้ session cookie (SameSite=Strict) + JSON body
            // จึงปิด CSRF token ไว้เพื่อความเรียบง่าย ถ้าเปิดให้โดเมนอื่นเรียก API ควรเปิดกลับและใช้ CookieCsrfTokenRepository
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // จำกัดจำนวนครั้งที่ยิง /api/auth/login และ /api/auth/forgot-password ต่อ IP
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> {
                // H2 console ใช้ iframe จึงต้อง sameOrigin — ถ้าปิด console แล้วก็ห้าม iframe ไปเลย
                if (h2ConsoleEnabled) {
                    h.frameOptions(f -> f.sameOrigin());
                } else {
                    h.frameOptions(f -> f.deny());
                }
                // security header เพิ่มเติม ใช้ StaticHeadersWriter เพื่อให้รองรับทุกเวอร์ชันของ Spring Security
                h.addHeaderWriter(new StaticHeadersWriter(
                        "Referrer-Policy", "strict-origin-when-cross-origin"));
                h.addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=()"));
            })
            .authorizeHttpRequests(auth -> auth
                // ต้องอยู่ก่อนกฎอื่นเสมอ เพราะ Spring Security ใช้กฎแรกที่ match กับ path
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/likes/**").authenticated()
                .requestMatchers("/api/account/**").authenticated()
                // อ่านรีวิวได้ทุกคน แต่เขียน/ลบต้องล็อกอินก่อน
                .requestMatchers(HttpMethod.GET, "/api/places/*/reviews").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/places/*/reviews").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/places/*/reviews/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, ex) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"เฉพาะผู้ดูแลระบบ (ADMIN) เท่านั้นที่เข้าใช้งานส่วนนี้ได้\"}");
                }));
        return http.build();
    }
}
