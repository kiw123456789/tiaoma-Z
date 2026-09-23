package com.tiaoma.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * รีวิวและคะแนนดาวของสถานที่ท่องเที่ยว
 * ผู้ใช้หนึ่งคนรีวิวสถานที่หนึ่งแห่งได้ครั้งเดียว (แก้ไขทับของเดิมได้)
 */
@Entity
@Table(name = "reviews",
       uniqueConstraints = @UniqueConstraint(name = "uk_review_user_place",
                                             columnNames = {"user_id", "place_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false, length = 100)
    private String placeId;

    /** 1–5 ดาว */
    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Review() {
    }

    public Review(Long userId, String placeId, int rating, String comment) {
        this.userId = userId;
        this.placeId = placeId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPlaceId() { return placeId; }
    public void setPlaceId(String placeId) { this.placeId = placeId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
