package com.tiaoma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** ใช้เกณฑ์เดียวกับตอนสมัครสมาชิก (ดู AuthDtos) */
    private static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z\\u0E00-\\u0E7F])(?=.*\\d).{8,72}$";
    private static final String PASSWORD_MESSAGE =
            "รหัสผ่านต้องมี 8-72 ตัวอักษร และมีทั้งตัวอักษรกับตัวเลขผสมกัน";

    public record UpdateProfileRequest(
            @NotBlank(message = "กรุณากรอกชื่อ")
            @Size(max = 100, message = "ชื่อยาวเกินไป (สูงสุด 100 ตัวอักษร)")
            String name) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "กรุณากรอกรหัสผ่านปัจจุบัน")
            String currentPassword,

            @NotBlank(message = "กรุณากรอกรหัสผ่านใหม่")
            @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String newPassword) {
    }

    public record DeleteAccountRequest(
            @NotBlank(message = "กรุณากรอกรหัสผ่านเพื่อยืนยัน")
            String password) {
    }
}
