package com.tiaoma.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/** เสิร์ฟรูปที่แอดมินอัปโหลด (โฟลเดอร์ uploads/) ผ่าน URL /uploads/** */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Service worker และ manifest ต้องห้าม cache
        // ไฟล์ static ทั่วไปตั้ง cache ไว้ 7 วัน ถ้า sw.js โดนด้วย ผู้ใช้จะติดอยู่กับ
        // service worker ตัวเก่านานถึง 7 วัน และไม่ได้รับการแก้ไขใดๆ เลย
        // (ต้องลงทะเบียนก่อน handler ของ /** เพราะ Spring ใช้ตัวแรกที่ตรง)
        registry.addResourceHandler("/sw.js", "/site.webmanifest")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(dir.toUri().toString())
                // ชื่อไฟล์เป็น UUID ไม่ซ้ำ จึง cache ได้ยาวๆ
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
