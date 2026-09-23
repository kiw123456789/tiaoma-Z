package com.tiaoma.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * ส่งอีเมลรีเซ็ตรหัสผ่าน
 * ถ้ายังไม่ได้ตั้ง spring.mail.host (ไม่มี JavaMailSender) จะพิมพ์ลิงก์ลง console แทน เหมาะกับตอนพัฒนา
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${app.mail.from}") String from) {
        this.senderProvider = senderProvider;
        this.from = from;
    }

    public void sendPasswordReset(String to, String name, String link) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            log.info("\n===== [DEV] ยังไม่ได้ตั้งค่า SMTP — ลิงก์รีเซ็ตรหัสผ่านของ {} =====\n{}\n", to, link);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("ตั้งรหัสผ่านใหม่ | เที่ยวมะ");
            helper.setText("สวัสดีคุณ " + name + "\n\n"
                    + "เราได้รับคำขอตั้งรหัสผ่านใหม่สำหรับบัญชีของคุณ กดลิงก์ด้านล่างเพื่อตั้งรหัสผ่านใหม่ "
                    + "(ลิงก์ใช้ได้ 30 นาที และใช้ได้ครั้งเดียว)\n\n"
                    + link + "\n\n"
                    + "หากคุณไม่ได้เป็นผู้ขอ สามารถเพิกเฉยต่ออีเมลนี้ได้\n\nทีมเที่ยวมะ");
            sender.send(message);
        } catch (Exception e) {
            // ไม่ให้ผู้ใช้รู้ว่าส่งไม่สำเร็จ (กันการเดาว่าอีเมลนี้มีในระบบ) แต่บันทึกไว้ให้ผู้ดูแล
            log.error("ส่งอีเมลรีเซ็ตรหัสผ่านไปที่ {} ไม่สำเร็จ", to, e);
        }
    }
}
