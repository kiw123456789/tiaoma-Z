package com.tiaoma.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity(name = "UserLike") // กันชื่อ entity ชนกับคีย์เวิร์ด LIKE ของ JPQL
@Table(name = "likes")
public class Like {

    @EmbeddedId
    private LikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Like() {
    }

    public Like(Long userId, String placeId) {
        this.id = new LikeId(userId, placeId);
    }

    public LikeId getId() {
        return id;
    }

    public void setId(LikeId id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ----- คีย์ผสม (composite key): user_id + place_id -----
    @Embeddable
    public static class LikeId implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "place_id")
        private String placeId;

        public LikeId() {
        }

        public LikeId(Long userId, String placeId) {
            this.userId = userId;
            this.placeId = placeId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getPlaceId() {
            return placeId;
        }

        public void setPlaceId(String placeId) {
            this.placeId = placeId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LikeId)) return false;
            LikeId likeId = (LikeId) o;
            return Objects.equals(userId, likeId.userId) && Objects.equals(placeId, likeId.placeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, placeId);
        }
    }
}