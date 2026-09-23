package com.tiaoma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "กรุณากรอกชื่อ-นามสกุล")
            @Size(max = 100, message = "ชื่อยาวเกินไป (สูงสุด 100 ตัวอักษร)")
            String name,

            @NotBlank(message = "กรุณากรอกอีเมล")
            @Email(message = "กรุณากรอกอีเมลให้ถูกต้อง")
            @Size(max = 254, message = "อีเมลยาวเกินไป")
            String email,

            @NotBlank(message = "กรุณากรอกรหัสผ่าน")
            @Size(min = 6, max = 72, message = "รหัสผ่านต้องมี 6-72 ตัวอักษร")
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
            @Size(min = 6, max = 72, message = "รหัสผ่านต้องมี 6-72 ตัวอักษร")
            String password) {
    }
}
