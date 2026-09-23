package com.tiaoma.controller;

import com.tiaoma.dto.ReviewDtos.ReviewRequest;
import com.tiaoma.model.Review;
import com.tiaoma.model.User;
import com.tiaoma.repository.PlaceRepository;
import com.tiaoma.repository.ReviewRepository;
import com.tiaoma.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** รีวิวและให้ดาวสถานที่ท่องเที่ยว — อ่านได้ทุกคน เขียน/ลบต้องล็อกอิน */
@RestController
@RequestMapping("/api/places/{placeId}/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository,
                            PlaceRepository placeRepository,
                            UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
    }

    /** รีวิวทั้งหมดของสถานที่นี้ + คะแนนเฉลี่ย + รีวิวของผู้ใช้ที่ล็อกอินอยู่ (ถ้ามี) */
    @GetMapping
    public Map<String, Object> list(@PathVariable String placeId, Authentication auth) {
        requirePlace(placeId);

        List<Review> reviews = reviewRepository.findByPlaceIdOrderByCreatedAtDesc(placeId);

        // ดึงชื่อผู้เขียนทั้งหมดในคิวรี่เดียว กัน N+1
        Map<Long, String> names = userRepository.findAllById(
                        reviews.stream().map(Review::getUserId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getName));

        Long currentUserId = currentUserIdOrNull(auth);

        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average", Math.round(average * 10) / 10.0);
        result.put("count", reviews.size());
        result.put("myReviewId", reviews.stream()
                .filter(r -> r.getUserId().equals(currentUserId))
                .map(Review::getId)
                .findFirst()
                .orElse(null));
        result.put("items", reviews.stream()
                .map(r -> view(r, names.getOrDefault(r.getUserId(), "ผู้ใช้"), currentUserId))
                .toList());
        return result;
    }

    /** เขียนรีวิวใหม่ หรือแก้ไขรีวิวเดิมของตัวเอง (หนึ่งคนหนึ่งรีวิวต่อสถานที่) */
    @PutMapping
    @Transactional
    public Map<String, Object> upsert(@PathVariable String placeId,
                                      @Valid @RequestBody ReviewRequest req,
                                      Authentication auth) {
        requirePlace(placeId);
        User user = currentUser(auth);

        String comment = req.comment() == null ? "" : req.comment().trim();
        if (comment.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ข้อความรีวิวยาวเกินไป (สูงสุด 1000 ตัวอักษร)");
        }

        Review review = reviewRepository.findByUserIdAndPlaceId(user.getId(), placeId)
                .orElseGet(() -> new Review(user.getId(), placeId, req.rating(), comment));

        review.setRating(req.rating());
        review.setComment(comment);
        if (review.getId() != null) {
            review.setUpdatedAt(LocalDateTime.now());
        }
        reviewRepository.save(review);

        return view(review, user.getName(), user.getId());
    }

    /** ลบรีวิวของตัวเอง (แอดมินลบของใครก็ได้) */
    @DeleteMapping("/{reviewId}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String placeId,
                                       @PathVariable Long reviewId,
                                       Authentication auth) {
        User user = currentUser(auth);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบรีวิวนี้"));

        if (!review.getPlaceId().equals(placeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบรีวิวนี้");
        }
        boolean isOwner = review.getUserId().equals(user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ลบได้เฉพาะรีวิวของตัวเองเท่านั้น");
        }

        reviewRepository.delete(review);
        return ResponseEntity.noContent().build();
    }

    private void requirePlace(String placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสถานที่นี้");
        }
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบก่อน");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบใหม่"));
    }

    private Long currentUserIdOrNull(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    private Map<String, Object> view(Review r, String authorName, Long currentUserId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rating", r.getRating());
        m.put("comment", r.getComment());
        m.put("authorName", authorName);
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        m.put("mine", r.getUserId().equals(currentUserId));
        return m;
    }
}
