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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 *
 * ตั้งแต่รอบนี้เป็นต้นไป ยังช่วยย่อ + บีบอัดรูปให้อัตโนมัติด้วย:
 *   - ถ้ารูปกว้างเกิน app.upload.max-resize-width จะย่อลงมา (คงสัดส่วนเดิม)
 *   - บีบใหม่เป็น JPEG คุณภาพ ~82% (ตาแทบแยกไม่ออก แต่ไฟล์เล็กลงมาก)
 *   - PNG ที่มีพื้นหลังโปร่งใส (เช่น โลโก้) คงไว้เป็น PNG แล้วบีบด้วย Deflater
 * ทั้งหมดใช้ javax.imageio ที่ติดมากับ JDK อยู่แล้ว — ไม่ต้องเพิ่มไลบรารีใดๆ
 */
@RestController
@RequestMapping("/api/admin/uploads")
public class AdminUploadController {

    private static final Logger log = LoggerFactory.getLogger(AdminUploadController.class);

    /** ขนาดไฟล์ต้นฉบับสูงสุดที่รับได้ (ก่อนย่อ) */
    private static final long MAX_BYTES = 5L * 1024 * 1024;           // 5 MB
    /** กันรูปเล็กจนเบลอ */
    private static final int MIN_WIDTH = 800;
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadDir;
    private final int maxResizeWidth;

    public AdminUploadController(
            @Value("${app.upload.dir:./uploads}") String dir,
            @Value("${app.upload.max-resize-width:1200}") String maxResizeWidthRaw) throws IOException {
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        this.maxResizeWidth = parseWidth(maxResizeWidthRaw, 1200);
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

        try (InputStream probe = file.getInputStream()) {
            if (ImageIO.read(probe) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "ไฟล์นี้ไม่ใช่รูปภาพที่อ่านได้หรือรูปแบบไม่รองรับ กรุณาเลือกไฟล์ใหม่");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "อ่านไฟล์รูปไม่สำเร็จ กรุณาลองใหม่");
        }

        Integer sourceWidth = readWidth(file);

        String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // สุ่มชื่อไฟล์แบบไม่มีนามสกุลก่อน — buildImage จะเติม .jpg / .png ให้ตามผลการบีบอัดจริง
        String stem = UUID.randomUUID().toString();
        Path target = uploadDir.resolve(folder).resolve(stem).normalize();
        // กัน path traversal (ถึงชื่อไฟล์จะสุ่มเองแล้วก็ตรวจซ้ำไว้)
        if (!target.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ชื่อไฟล์ไม่ถูกต้อง");
        }

        try {
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException e) {
                log.error("สร้างโฟลเดอร์อัปโหลดไม่สำเร็จ", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "เตรียมพื้นที่เก็บไฟล์ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
            }

            SavedImage saved = buildImage(target, file, contentType);

            String url = "/uploads/" + folder + "/" + saved.path().getFileName();
            String resizeInfo = buildResizeInfo(file.getSize(), saved);
            String warning = (sourceWidth != null && sourceWidth < MIN_WIDTH)
                    ? "รูปต้นฉบับกว้างแค่ " + sourceWidth + "px ต่ำกว่า " + MIN_WIDTH + "px อาจเบลอตอนแสดงผลใหญ่"
                    : "";
            log.info("อัปโหลดรูปใหม่: {} ({} -> {} กว้าง {}px)",
                    url, humanBytes(file.getSize()), humanBytes(saved.sizeBytes()), saved.width());

            return Map.of(
                    "url", url,
                    "width", saved.width(),
                    "sizeBytes", saved.sizeBytes(),
                    "warning", warning,
                    "info", resizeInfo);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            log.error("บันทึกไฟล์อัปโหลดไม่สำเร็จ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "บันทึกไฟล์ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง");
        }
    }

    /** บันทึกภาพลงดิสก์ ย่อ + บีบตามชนิดไฟล์ แล้วคืนผลลัพธ์ (path จริงอาจเป็น .jpg ไม่ใช่ .png เดิม)
     *  target คือพาธ "แบบไม่มีนามสกุล" — ฟังก์ชันนี้จะเป็นคนเติมนามสกุลให้เอง */
    private SavedImage buildImage(Path target, MultipartFile file, String contentType)
            throws ResponseStatusException, IOException {
        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ไฟล์นี้ไม่ใช่รูปภาพที่อ่านได้ กรุณาเลือกไฟล์ใหม่");
        }

        boolean isPng = "image/png".equals(contentType);
        boolean keepAlpha = isPng && original.getColorModel().hasAlpha();

        // 1) ย่อขนาดถ้ากว้างเกินกำหนด (คงสัดส่วนเสมอ)
        BufferedImage img = original;
        if (original.getWidth() > maxResizeWidth) {
            int newHeight = Math.max(1,
                    (int) Math.round(original.getHeight() * ((double) maxResizeWidth / original.getWidth())));
            img = scale(original, maxResizeWidth, newHeight, keepAlpha);
        }

        if (isPng && keepAlpha) {
            // 2a) PNG โปร่งใส: คงเป็น PNG แต่บีบด้วย Deflater
            Path png = target.getParent().resolve(stripExt(target.getFileName().toString()) + ".png");
            writePng(img, png);
            return new SavedImage(png, img.getWidth(), Files.size(png));
        }

        // 2b) JPG / WebP / PNG ทึบแสง: แปลงเป็น JPEG คุณภาพ ~82%
        byte[] jpeg = encodeJpeg(img, 0.82f);
        Path jpg = target.getParent().resolve(stripExt(target.getFileName().toString()) + ".jpg");
        Files.write(jpg, jpeg);
        return new SavedImage(jpg, img.getWidth(), jpeg.length);
    }

    /** ย่อรูปด้วย Bicubic interpolation (คมกว่าวิธี Java ให้มาโดย baseline) */
    private BufferedImage scale(BufferedImage src, int w, int h, boolean withAlpha) {
        BufferedImage dest = new BufferedImage(w, h,
                withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (withAlpha) {
                g.setComposite(AlphaComposite.Src); // ล้างพิกเซลเดิมให้หมด แล้ววาดลงไปใหม่
            } else {
                g.setColor(Color.WHITE);            // JPEG ไม่มี alpha ต้องเติมพื้นหลังทึบก่อน
                g.fillRect(0, 0, w, h);
            }
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    private void writePng(BufferedImage img, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (OutputStream out = Files.newOutputStream(target);
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("Deflater");
            param.setCompressionQuality(0.72f);
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();   // บังคับเขียนข้อมูลออกก่อน stream ปิด
            ios.flush();
        } finally {
            writer.dispose();
        }
        if (!Files.exists(target) || Files.size(target) == 0) {
            throw new IOException("เขียน PNG ไม่สมบูรณ์");
        }
    }

    private byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            try {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(img, null, null), param);
                writer.dispose();   // บังคับเขียนข้อมูลออกก่อน stream ปิด
                ios.flush();
            } catch (IOException e) {
                writer.dispose();
                throw e;
            }
            return baos.toByteArray();
        }
    }

    /** อ่านความกว้างต้นฉบับแบบไม่โยน exception (ใช้แค่ทำคำเตือน ไม่ใช่จุดตรวจหลัก) */
    private Integer readWidth(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            return img == null ? null : img.getWidth();
        } catch (IOException e) {
            return null;
        }
    }

    /** ข้อความแจ้งผลการบีบอัด — ว่างถ้าไม่ได้ทำอะไร (รูปเล็กอยู่แล้ว) */
    private String buildResizeInfo(long srcBytes, SavedImage saved) {
        if (saved.sizeBytes() < srcBytes) {
            return "ระบบย่อ + บีบรูปให้อัตโนมัติ: " + humanBytes(srcBytes)
                    + " → " + humanBytes(saved.sizeBytes())
                    + " (กว้าง " + saved.width() + "px)";
        }
        return "";
    }

    /** อ่านค่าที่ตั้งไว้ทนๆ — รับทั้ง "1200", "1200px", "max-width: 1200" แล้วดึงเฉพาะตัวเลข */
    private int parseWidth(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        String digits = value.trim().replaceAll("^\\D+", "").replaceAll("\\D+$", "");
        try {
            int n = Integer.parseInt(digits);
            return n >= 100 ? n : fallback; // กันตั้งผิดจนเล็กจิ๋ว เช่น "80"
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private String humanBytes(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
    }

    /** ผลลัพธ์ที่บันทึกสำเร็จ: path จริง (นามสกุลอาจเปลี่ยน) + ขนาดหลังย่อ */
    private record SavedImage(Path path, int width, long sizeBytes) {}
}