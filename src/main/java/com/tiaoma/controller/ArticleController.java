package com.tiaoma.controller;

import com.tiaoma.model.Article;
import com.tiaoma.repository.ArticleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleRepository articleRepository;

    public ArticleController(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * รายการบทความ — ไม่ส่งฟิลด์ content (เนื้อหาเต็ม) กลับมาด้วย
     * เพราะหน้ารายการใช้แค่คำโปรย การส่ง content ทุกชิ้นทำให้ response ใหญ่เกินจำเป็น
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String category) {
        List<Article> articles = (category == null || category.isBlank())
                ? articleRepository.findAllByOrderByPublishedAtDesc()
                : articleRepository.findByCategoryOrderByPublishedAtDesc(category.trim());
        return articles.stream().map(this::summaryView).toList();
    }

    /** หมวดหมู่ทั้งหมดที่มีบทความอยู่จริง (ใช้ทำปุ่มกรองในหน้าบทความ) */
    @GetMapping("/categories")
    public List<String> categories() {
        return articleRepository.findDistinctCategories();
    }

    /** บทความฉบับเต็ม พร้อมลิงก์บทความก่อนหน้า/ถัดไป */
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบบทความนี้"));

        Map<String, Object> view = summaryView(article);
        view.put("content", article.getContent());

        // บทความอื่นในหมวดเดียวกัน (สูงสุด 3 ชิ้น) ไว้แนะนำท้ายบทความ
        List<Map<String, Object>> related = articleRepository
                .findByCategoryOrderByPublishedAtDesc(article.getCategory()).stream()
                .filter(a -> !a.getId().equals(id))
                .limit(3)
                .map(this::summaryView)
                .toList();
        view.put("related", related);
        return view;
    }

    private Map<String, Object> summaryView(Article a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("summary", a.getSummary());
        m.put("image", a.getImage());
        m.put("category", a.getCategory());
        m.put("publishedAt", a.getPublishedAt());
        // ประมาณเวลาอ่าน ~450 ตัวอักษรไทยต่อนาที
        int len = a.getContent() == null ? 0 : a.getContent().length();
        m.put("readMinutes", Math.max(1, (int) Math.ceil(len / 450.0)));
        return m;
    }
}
