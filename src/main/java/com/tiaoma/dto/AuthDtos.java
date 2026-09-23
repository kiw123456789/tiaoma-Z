package com.tiaoma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * รหัสผ่านต้องมีทั้งตัวอักษร (อังกฤษหรือไทย) และตัวเลขผสมกัน
     * ตรวจฝั่งเซิร์ฟเวอร์ด้วย ไม่ใช่แค่ฝั่งหน้าเว็บ เพราะ JS ถูกข้ามได้ง่าย
     */
    private static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z\\u0E00-\\u0E7F])(?=.*\\d).{8,72}$";
    private static final String PASSWORD_MESSAGE =
            "รหัสผ่านต้องมี 8-72 ตัวอักษร และมีทั้งตัวอักษรกับตัวเลขผสมกัน";

    public record RegisterRequest(
            @NotBlank(message = "กรุณากรอกชื่อ-นามสกุล")
            @Size(max = 100, message = "ชื่อยาวเกินไป (สูงสุด 100 ตัวอักษร)")
            String name,

            @NotBlank(message = "กรุณากรอกอีเมล")
            @Email(message = "กรุณากรอกอีเมลให้ถูกต้อง")
            @Size(max = 254, message = "อีเมลยาวเกินไป")
            String email,

            @NotBlank(message = "กรุณากรอกรหัสผ่าน")
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "กรุณากรอกอีเมลและรหัสผ่าน") String email,
            @NotBlank(message = "กรุณากรอกอีเมลและรหัสผ่าน") String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "กรุณากรอกอีเมล")
            @Email(message = "กรุณากรอกอีเมลให้ถูกต้อง")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "ลิงก์ไม่ถูกต้อง") String token,

            @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password) {
    }
}
