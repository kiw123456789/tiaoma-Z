package com.tiaoma.service;

import com.tiaoma.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** ให้ Spring Security ค้นผู้ใช้จากอีเมลในฐานข้อมูล (username = อีเมล) */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        com.tiaoma.model.User user = userRepository.findByEmail(AuthService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("ไม่พบผู้ใช้"));
        // .roles("ADMIN") หรือ .roles("USER") -> Spring จะเติม prefix เป็น ROLE_ADMIN / ROLE_USER ให้เอง
        // (ใช้คู่กับ hasRole("ADMIN") ใน SecurityConfig)
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }
}
