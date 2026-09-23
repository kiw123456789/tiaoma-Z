package com.tiaoma.controller;

import com.tiaoma.model.Place;
import com.tiaoma.repository.PlaceRepository;
import com.tiaoma.service.PlaceViewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceRepository placeRepository;
    private final PlaceViewService placeViewService;

    public PlaceController(PlaceRepository placeRepository, PlaceViewService placeViewService) {
        this.placeRepository = placeRepository;
        this.placeViewService = placeViewService;
    }

    /** สถานที่ทั้งหมด พร้อมคะแนนเฉลี่ยและจำนวนคนบันทึก (คำนวณรวมทีเดียว กัน N+1) */
    @GetMapping
    public List<Map<String, Object>> list() {
        return placeViewService.withStats(placeRepository.findAll());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสถานที่นี้"));
        return placeViewService.withStats(List.of(place)).get(0);
    }
}
