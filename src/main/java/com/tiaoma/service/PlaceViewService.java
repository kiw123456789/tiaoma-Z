package com.tiaoma.service;

import com.tiaoma.model.Place;
import com.tiaoma.repository.LikeRepository;
import com.tiaoma.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * แปลง Place เป็น JSON ที่หน้าเว็บใช้ พร้อมแนบสถิติ (คะแนนเฉลี่ย, จำนวนรีวิว, จำนวนคนบันทึก)
 *
 * ดึงสถิติของทุกสถานที่มาในสองคิวรี่รวด แล้วค่อยจับคู่ในหน่วยความจำ
 * แทนที่จะยิงคิวรี่ต่อสถานที่ (N+1) ซึ่งช้ามากเมื่อมีสถานที่เยอะ
 */
@Service
public class PlaceViewService {

    private final ReviewRepository reviewRepository;
    private final LikeRepository likeRepository;

    public PlaceViewService(ReviewRepository reviewRepository, LikeRepository likeRepository) {
        this.reviewRepository = reviewRepository;
        this.likeRepository = likeRepository;
    }

    public List<Map<String, Object>> withStats(List<Place> places) {
        Map<String, double[]> ratings = new HashMap<>();   // placeId -> [average, count]
        for (Object[] row : reviewRepository.findRatingSummaries()) {
            ratings.put((String) row[0], new double[]{
                    ((Number) row[1]).doubleValue(),
                    ((Number) row[2]).doubleValue()
            });
        }

        Map<String, Long> likes = new HashMap<>();
        for (Object[] row : likeRepository.countGroupedByPlace()) {
            likes.put((String) row[0], ((Number) row[1]).longValue());
        }

        return places.stream().map(p -> toView(p, ratings, likes)).toList();
    }

    private Map<String, Object> toView(Place p, Map<String, double[]> ratings, Map<String, Long> likes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("province", p.getProvince());
        m.put("category", p.getCategory());
        m.put("short", p.getShortText());
        m.put("description", p.getDescription());
        m.put("highlights", p.getHighlights());
        m.put("hours", p.getHours());
        m.put("fee", p.getFee());
        m.put("bestTime", p.getBestTime());
        m.put("mapQuery", p.getMapQuery());
        m.put("image", p.getImage());
        m.put("accent", p.getAccent());

        double[] r = ratings.get(p.getId());
        m.put("ratingAverage", r == null ? 0.0 : Math.round(r[0] * 10) / 10.0);
        m.put("ratingCount", r == null ? 0 : (int) r[1]);
        m.put("likeCount", likes.getOrDefault(p.getId(), 0L));
        return m;
    }
}
