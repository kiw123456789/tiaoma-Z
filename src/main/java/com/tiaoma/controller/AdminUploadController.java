package com.tiaoma.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * อัปโหลดรูปสำหรับหน้าจัดการเนื้อหา — เฉพาะ ADMIN
 *
 * เดิมหน้า admin แปลงรูปเป็น base64 data URL แล้วเก็บลงฐานข้อมูล ซึ่งมีปัญหา:
 *   - JSON ของ /api/places ใหญ่มาก (รูปทุกใบถูกส่งมาด้วยทุกครั้งที่เปิดหน้า)
 *   - เบราว์เซอร์ cache รูปไม่ได้เลย
 *   - ฐานข้อมูลบวมเร็ว และไม่มีการจำกัดขนาด
 *
 * ตัวนี้เก็บเป็นไฟล์จริงในโฟลเดอร์ uploads/ แล้วคืน path กลับไปให้เก็บใน DB แทน
 */
@RestController
@RequestMapping("/api/admin/uploads")
public class AdminUploadController {

    private static final Logger log = LoggerFactory.getLogger(AdminUploadController.class);

    private static final long MAX_BYTES = 5L * 1024 * 1024;           // 5 MB
    private static final int MIN_WIDTH = 800;                          // กันรูปเล็กจนเบลอ
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path uploadDir;

    public AdminUploadController(@Value("${app.upload.dir:./uploads}") String dir) throws IOException {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    @PostMapping
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ไม่พบไฟล์ที่อัปโหลด");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "ไฟล์ใหญ่เกินไป (สูงสุด 5 MB) กรุณาย่อรูปก่อนอัปโหลด");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "รองรับเฉพาะไฟล์ JPG, PNG และ WebP เท่านั้น");
        }

        // ตรวจว่าเป็นไฟล์รูปจริง ไม่ใช่ไฟล์อื่นที่แค่ตั้งชื่อ/ตั้ง content-type หลอกมา
        int width = 0;
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "ไฟล์นี้ไม่ใช่รูปภาพที่อ่านได้ กรุณาเลือกไฟล์ใหม่");
            }
            width = img.getWidth();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "อ่านไฟล์รูปไม่สำเร็จ กรุณาลองใหม่");
        }

        String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID() + "." + EXT.get(contentType);

        try {
            Path target = uploadDir.resolve(folder).resolve(filename).normalize();
            // กัน path traversal (ถึงชื่อไฟล์จะสุ่มเองแล้วก็ตรวจซ้ำไว้)
            if (!target.startsWith(uploadDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ชื่อไฟล์ไม่ถูกต้อง");
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/uploads/" + folder + "/" + filename;
            log.info("อัปโหลดรูปใหม่: {} ({} bytes, กว้าง {}px)", url, file.getSize(), width);

            return Map.of(
                    "url", url,
                    "width", width,
                    "sizeBytes", file.getSize(),
                    "warning", width < MIN_WIDTH
                            ? "รูปนี้กว้างแค่ " + width + "px แนะนำอย่างน้อย " + MIN_WIDTH + "px ไม่งั้นจะเบลอตอนแสดงผลใหญ่"
                            : "");
        } catch (IOException e) {
            log.error("บันทึกไฟล์อัปโหลดไม่สำเร็จ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "บันทึกไฟล์ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
        }
    }
}
