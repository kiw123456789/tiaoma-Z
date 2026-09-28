package com.tiaoma.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiaoma.model.Article;
import com.tiaoma.model.Place;
import com.tiaoma.model.User;
import com.tiaoma.repository.ArticleRepository;
import com.tiaoma.repository.PlaceRepository;
import com.tiaoma.repository.UserRepository;
import com.tiaoma.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ใส่ข้อมูลตั้งต้น (สถานที่ + บทความ + บัญชีแอดมินคนแรก) ครั้งแรกที่ฐานข้อมูลยังว่าง */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** ไฟล์ seed สถานที่ — แยกตามภาค (places.json คือไฟล์รวมเดิม ที่เหลือเป็นไฟล์รายภาค) */
    private static final String[] PLACE_FILES = {
            "seed/places.json",
            "seed/places-north.json",
            "seed/places-west.json",
            "seed/places-isan.json",
            "seed/places-central.json",
            "seed/places-east.json",
            "seed/places-south.json"
    };

    private final PlaceRepository placeRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(PlaceRepository placeRepository, ArticleRepository articleRepository,
                       UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ObjectMapper objectMapper,
                       @Value("${app.admin.name}") String adminName,
                       @Value("${app.admin.email}") String adminEmail,
                       @Value("${app.admin.password:}") String adminPassword) {
        this.placeRepository = placeRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) throws Exception {
        seedPlaces();

        if (articleRepository.count() == 0) {
            ClassPathResource articlesResource = new ClassPathResource("seed/articles.json");
            if (articlesResource.exists()) {
                try (InputStream in = articlesResource.getInputStream()) {
                    List<Article> articles = objectMapper.readValue(in, new TypeReference<List<Article>>() {});
                    articleRepository.saveAll(articles);
                    log.info("[tiaoma] เพิ่มบทความตั้งต้น {} ชิ้น", articles.size());
                }
            } else {
                log.warn("[tiaoma] ไม่พบ seed/articles.json จึงข้ามการใส่บทความตั้งต้น");
            }
        }

        seedFirstAdmin();
    }

    /**
     * seed สถานที่จากหลายไฟล์ตามลำดับ PLACE_FILES — เติมเฉพาะ id ที่ยังไม่มีในตาราง
     * (idempotent: รีบูตกี่ครั้งก็ไม่ทับรายการเดิม และข้ามไฟล์ที่ยังไม่มี)
     */
    private void seedPlaces() throws Exception {
        Set<String> existingIds = new HashSet<>();
        placeRepository.findAll().forEach(p -> existingIds.add(p.getId()));

        List<Place> newPlaces = new ArrayList<>();
        for (String file : PLACE_FILES) {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                List<Place> places = objectMapper.readValue(in, new TypeReference<List<Place>>() {});
                for (Place place : places) {
                    if (existingIds.add(place.getId())) {
                        newPlaces.add(place);
                    }
                }
            } catch (Exception e) {
                throw new Exception("อ่านไฟล์ seed ไม่สำเร็จ: " + file, e);
            }
        }

        if (!newPlaces.isEmpty()) {
            placeRepository.saveAll(newPlaces);
        }
        long total = placeRepository.count();
        if (newPlaces.isEmpty()) {
            log.info("[tiaoma] สถานที่ตั้งต้นครบแล้ว {} แห่ง (ไม่มีรายการใหม่)", total);
        } else {
            log.info("[tiaoma] เพิ่มสถานที่ตั้งต้น {} แห่ง (รวมทั้งหมด {} แห่ง)", newPlaces.size(), total);
        }
    }
    /**
     * ถ้ายังไม่มีแอดมินในระบบเลย ให้สร้าง/เลื่อนขั้นบัญชีตาม app.admin.email ที่ตั้งค่าไว้
     * (ถ้าอีเมลนี้สมัครสมาชิกไว้แล้ว จะแค่เลื่อนขั้นให้ ไม่สร้างซ้ำหรือทับรหัสผ่านเดิม)
     *
     * ถ้าไม่ได้ตั้ง APP_ADMIN_PASSWORD ไว้ ระบบจะสุ่มรหัสผ่านให้แล้วพิมพ์ลง console ครั้งเดียว
     * ดีกว่าการฝังรหัสผ่าน default ไว้ในโค้ดซึ่งใครอ่าน repo ก็รู้
     */
    private void seedFirstAdmin() {
        if (userRepository.countByRole(User.Role.ADMIN) > 0) {
            return;
        }

        String normalizedEmail = AuthService.normalizeEmail(adminEmail);
        User existing = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (existing != null) {
            existing.setRole(User.Role.ADMIN);
            userRepository.save(existing);
            log.info("[tiaoma] เลื่อนขั้นบัญชีที่มีอยู่แล้วเป็นแอดมิน: {}", normalizedEmail);
            return;
        }

        boolean generated = adminPassword == null || adminPassword.isBlank();
        String password = generated ? randomPassword() : adminPassword;

        User admin = new User(adminName, normalizedEmail, passwordEncoder.encode(password));
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);

        if (generated) {
            log.warn("""

                    ==========================================================
                     [tiaoma] สร้างบัญชีแอดมินคนแรกแล้ว
                     อีเมล   : {}
                     รหัสผ่าน : {}
                     *** รหัสนี้แสดงครั้งเดียวเท่านั้น กรุณาบันทึกไว้
                         แล้วเข้าไปเปลี่ยนรหัสผ่านที่หน้าโปรไฟล์ทันที ***
                     (ตั้ง environment variable APP_ADMIN_PASSWORD เพื่อกำหนดเอง)
                    ==========================================================
                    """, normalizedEmail, password);
        } else {
            log.info("[tiaoma] สร้างบัญชีแอดมินคนแรกแล้ว: {} — เข้าสู่ระบบแล้วเปลี่ยนรหัสผ่านทันที", normalizedEmail);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}