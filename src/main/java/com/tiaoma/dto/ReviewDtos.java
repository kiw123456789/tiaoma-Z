package com.tiaoma.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ReviewRequest(
            @Min(value = 1, message = "กรุณาให้คะแนน 1-5 ดาว")
            @Max(value = 5, message = "กรุณาให้คะแนน 1-5 ดาว")
            int rating,

            @Size(max = 1000, message = "ข้อความรีวิวยาวเกินไป (สูงสุด 1000 ตัวอักษร)")
            String comment) {
    }
}
