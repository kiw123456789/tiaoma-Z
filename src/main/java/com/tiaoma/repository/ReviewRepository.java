package com.tiaoma.repository;

import com.tiaoma.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPlaceIdOrderByCreatedAtDesc(String placeId);

    Optional<Review> findByUserIdAndPlaceId(Long userId, String placeId);

    long countByPlaceId(String placeId);

    void deleteByPlaceId(String placeId);

    void deleteByUserId(Long userId);

    /** คะแนนเฉลี่ยและจำนวนรีวิวของทุกสถานที่ในคิวรี่เดียว (กัน N+1 ตอนวาดรายการ) */
    @Query("select r.placeId, avg(r.rating), count(r) from Review r group by r.placeId")
    List<Object[]> findRatingSummaries();
}
