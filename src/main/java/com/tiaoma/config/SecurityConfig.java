package com.tiaoma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

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
            // หน้าเว็บและ API อยู่ origin เดียวกัน ใช้ session cookie (SameSite=Lax) + JSON body
            // จึงปิด CSRF token ไว้เพื่อความเรียบง่าย ถ้าเปิดให้โดเมนอื่นเรียก API ควรเปิดกลับและใช้ CookieCsrfTokenRepository
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // อนุญาต H2 console (ใช้ iframe) เฉพาะตอนพัฒนา — ควรปิดตอนขึ้นระบบจริง
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                // ต้องอยู่ก่อนกฎอื่นเสมอ เพราะ Spring Security ใช้กฎแรกที่ match กับ path
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/likes/**").authenticated()
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
