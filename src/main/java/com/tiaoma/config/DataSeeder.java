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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/** ใส่ข้อมูลตั้งต้น (สถานที่ + บทความ + บัญชีแอดมินคนแรก) ครั้งแรกที่ฐานข้อมูลยังว่าง */
@Component
public class DataSeeder implements CommandLineRunner {

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
                       @Value("${app.admin.password}") String adminPassword) {
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
        if (placeRepository.count() == 0) {
            try (InputStream in = new ClassPathResource("seed/places.json").getInputStream()) {
                List<Place> places = objectMapper.readValue(in, new TypeReference<List<Place>>() {});
                placeRepository.saveAll(places);
            }
        }

        if (articleRepository.count() == 0) {
            articleRepository.saveAll(List.of(
                new Article("10 ที่เที่ยวเชียงใหม่ ต้องไปสักครั้งในชีวิต",
                    "รวมจุดหมายยอดฮิตของเชียงใหม่ ตั้งแต่วัดเก่าแก่ไปจนถึงคาเฟ่วิวภูเขา เหมาะกับทริปสั้นๆ ช่วงวันหยุด",
                    "image/chiangmai-old-city.png", "ภาคเหนือ", LocalDate.of(2026, 6, 20)),
                new Article("เที่ยวทะเลใต้ยังไงให้คุ้ม งบไม่บาน",
                    "เทคนิควางแผนทริปทะเลภาคใต้ ทั้งเรื่องที่พัก การเดินทาง และช่วงเวลาที่ราคาตั๋วเครื่องบินถูกที่สุด",
                    "image/phi-phi.jpg", "ภาคใต้", LocalDate.of(2026, 6, 12)),
                new Article("แนะนำเส้นทางเดินป่าเขาใหญ่สำหรับมือใหม่",
                    "เส้นทางเดินป่าที่เหมาะกับผู้เริ่มต้น พร้อมของที่ควรเตรียมและข้อควรระวังก่อนออกเดินทาง",
                    "image/khao-yai.jpg", "ผจญภัย", LocalDate.of(2026, 6, 2)),
                new Article("เที่ยวอยุธยาแบบวันเดียว เที่ยวไหว ไม่ลืมจุดไหน",
                    "จัดตารางเที่ยวอยุธยาแบบวันเดียวจบ พร้อมร้านอาหารเด็ดใกล้แหล่งท่องเที่ยว",
                    "image/ayutthaya.jpg", "ประวัติศาสตร์", LocalDate.of(2026, 5, 25))
            ));
        }

        // ถ้ายังไม่มีแอดมินในระบบเลย ให้สร้าง/เลื่อนขั้นบัญชีตาม app.admin.email ที่ตั้งค่าไว้
        // (ถ้าอีเมลนี้สมัครสมาชิกไว้แล้ว จะแค่เลื่อนขั้นให้ ไม่สร้างซ้ำหรือทับรหัสผ่านเดิม)
        if (userRepository.countByRole(User.Role.ADMIN) == 0) {
            String normalizedEmail = AuthService.normalizeEmail(adminEmail);
            User admin = userRepository.findByEmail(normalizedEmail).orElseGet(() ->
                    new User(adminName, normalizedEmail, passwordEncoder.encode(adminPassword)));
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
            System.out.println("[tiaoma] ตั้งค่าบัญชีแอดมินเริ่มต้นแล้ว: " + normalizedEmail
                    + " — เข้าสู่ระบบแล้วเปลี่ยนรหัสผ่านทันทีถ้ายังใช้ค่า default จาก application.properties อยู่");
        }
    }
}
