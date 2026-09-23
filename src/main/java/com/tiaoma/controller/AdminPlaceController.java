package com.tiaoma.controller;

import com.tiaoma.dto.AdminDtos.PlaceRequest;
import com.tiaoma.model.Place;
import com.tiaoma.repository.LikeRepository;
import com.tiaoma.repository.PlaceRepository;
import com.tiaoma.repository.ReviewRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * เพิ่ม/แก้ไข/ลบสถานที่ท่องเที่ยว — เข้าถึงได้เฉพาะ ADMIN เท่านั้น
 * (การอ่านข้อมูล /api/places แบบสาธารณะยังอยู่ที่ PlaceController เดิม)
 */
@RestController
@RequestMapping("/api/admin/places")
public class AdminPlaceController {

    private final PlaceRepository placeRepository;
    private final LikeRepository likeRepository;
    private final ReviewRepository reviewRepository;

    public AdminPlaceController(PlaceRepository placeRepository,
                                LikeRepository likeRepository,
                                ReviewRepository reviewRepository) {
        this.placeRepository = placeRepository;
        this.likeRepository = likeRepository;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping
    public ResponseEntity<Place> create(@Valid @RequestBody PlaceRequest req) {
        String id = req.id() == null ? "" : req.id().trim();
        if (!id.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "รหัสสถานที่ (id) ต้องเป็นตัวพิมพ์เล็ก a-z, 0-9 และ - เท่านั้น เช่น doi-inthanon");
        }
        if (placeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "มีรหัสสถานที่นี้อยู่แล้ว กรุณาใช้รหัสอื่น");
        }
        Place place = new Place();
        place.setId(id);
        applyRequest(place, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(placeRepository.save(place));
    }

    @PutMapping("/{id}")
    public Place update(@PathVariable String id, @Valid @RequestBody PlaceRequest req) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสถานที่นี้"));
        applyRequest(place, req);
        return placeRepository.save(place);
    }

    /**
     * ลบสถานที่ พร้อมล้างข้อมูลที่อ้างถึงสถานที่นี้ (ที่เที่ยวโปรด + รีวิว)
     * ไม่งั้นจะเหลือแถวกำพร้าค้างในฐานข้อมูล และหน้า "ที่เที่ยวโปรด" จะพังเพราะหา place ไม่เจอ
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!placeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสถานที่นี้");
        }
        likeRepository.deleteByIdPlaceId(id);
        reviewRepository.deleteByPlaceId(id);
        placeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyRequest(Place place, PlaceRequest req) {
        place.setName(req.name().trim());
        place.setProvince(req.province().trim());
        place.setCategory(req.category().trim());
        place.setShortText(req.shortText().trim());
        place.setDescription(cleanLines(req.description()));
        place.setHighlights(cleanLines(req.highlights()));
        place.setHours(trimOrEmpty(req.hours()));
        place.setFee(trimOrEmpty(req.fee()));
        place.setBestTime(trimOrEmpty(req.bestTime()));
        place.setMapQuery(trimOrEmpty(req.mapQuery()));
        place.setImage(trimOrEmpty(req.image()));
        place.setAccent(req.accent() == null ? 1 : req.accent());
    }

    private static List<String> cleanLines(List<String> lines) {
        return lines == null ? List.of() : lines.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
