package com.tiaoma.controller;

import com.tiaoma.model.Like;
import com.tiaoma.repository.LikeRepository;
import com.tiaoma.repository.PlaceRepository;
import com.tiaoma.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** ที่เที่ยวโปรดของผู้ใช้ที่ล็อกอินอยู่ (SecurityConfig บังคับให้ต้องล็อกอินทุก endpoint ในนี้) */
@RestController
@RequestMapping("/api/likes")
public class LikeController {

    private final LikeRepository likeRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    public LikeController(LikeRepository likeRepository, PlaceRepository placeRepository, UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
    }

    /** รายการ id สถานที่ที่ผู้ใช้กดหัวใจไว้ (ล่าสุดก่อน) */
    @GetMapping
    public List<String> list(Authentication auth) {
        Long userId = currentUserId(auth);
        return likeRepository.findByIdUserIdOrderByCreatedAtDesc(userId).stream()
                .map(like -> like.getId().getPlaceId())
                .toList();
    }

    /** บันทึกที่เที่ยวโปรด (เรียกซ้ำได้ ผลเหมือนเดิม) */
    @PutMapping("/{placeId}")
    public ResponseEntity<Void> add(@PathVariable String placeId, Authentication auth) {
        if (!placeRepository.existsById(placeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสถานที่นี้");
        }
        Long userId = currentUserId(auth);
        Like.LikeId id = new Like.LikeId(userId, placeId);
        if (likeRepository.findById(id).isEmpty()) {
            try {
                likeRepository.save(new Like(userId, placeId));
            } catch (DataIntegrityViolationException ignored) {
                // กดซ้ำพร้อมกัน มีรายการอยู่แล้ว ถือว่าสำเร็จ
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** ยกเลิกที่เที่ยวโปรด (เรียกซ้ำได้ ผลเหมือนเดิม) */
    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> remove(@PathVariable String placeId, Authentication auth) {
        Long userId = currentUserId(auth);
        likeRepository.findById(new Like.LikeId(userId, placeId)).ifPresent(likeRepository::delete);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบใหม่"))
                .getId();
    }
}
