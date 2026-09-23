package com.tiaoma.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** body ที่ admin.js ส่งมาตอนเพิ่ม/แก้ไขสถานที่ (ชื่อ field ตรงกับ places-data.js เดิม) */
    public record PlaceRequest(
            String id,

            @NotBlank(message = "กรุณากรอกชื่อสถานที่")
            String name,

            @NotBlank(message = "กรุณากรอกจังหวัด")
            String province,

            @NotBlank(message = "กรุณากรอกหมวดหมู่")
            String category,

            @JsonProperty("short")
            @NotBlank(message = "กรุณากรอกคำอธิบายสั้น")
            String shortText,

            List<String> description,
            List<String> highlights,
            String hours,
            String fee,
            String bestTime,
            String mapQuery,
            String image,
            Integer accent) {
    }

    public record ArticleRequest(
            @NotBlank(message = "กรุณากรอกหัวข้อบทความ")
            String title,

            @NotBlank(message = "กรุณากรอกหมวดหมู่")
            String category,

            @NotNull(message = "กรุณาเลือกวันที่เผยแพร่")
            LocalDate publishedAt,

            String image,

            @NotBlank(message = "กรุณากรอกคำโปรยสั้น")
            String summary,

            String content) {
    }

    public record RoleUpdateRequest(
            @NotBlank(message = "กรุณาระบุ role")
            String role) {
    }
}
