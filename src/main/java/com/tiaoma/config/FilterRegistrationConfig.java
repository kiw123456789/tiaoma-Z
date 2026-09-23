package com.tiaoma.config;

import com.tiaoma.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ปิดการลงทะเบียน RateLimitFilter อัตโนมัติของ Spring Boot
 *
 * เนื่องจาก RateLimitFilter เป็น @Component ที่เป็น Filter ด้วย Spring Boot จะเสียบมันเข้า
 * servlet filter chain ให้เองโดยอัตโนมัติ และเรายังเสียบมันเข้า filter chain ของ Spring Security
 * อีกทีใน SecurityConfig ผลคือคำขอหนึ่งครั้งจะถูกนับสองครั้ง ทำให้โควตาหมดเร็วเป็นเท่าตัว
 *
 * ตัวนี้บอก Spring Boot ว่า "อย่าลงทะเบียนให้" ปล่อยให้ Spring Security จัดการที่เดียว
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> disableAutoRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
