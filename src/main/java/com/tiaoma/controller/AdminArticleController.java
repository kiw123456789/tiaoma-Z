package com.tiaoma.controller;

import com.tiaoma.dto.AdminDtos.ArticleRequest;
import com.tiaoma.model.Article;
import com.tiaoma.repository.ArticleRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * เพิ่ม/แก้ไข/ลบบทความ — เข้าถึงได้เฉพาะ ADMIN เท่านั้น
 * (การอ่านข้อมูล /api/articles แบบสาธารณะยังอยู่ที่ ArticleController เดิม)
 */
@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final ArticleRepository articleRepository;

    public AdminArticleController(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @PostMapping
    public ResponseEntity<Article> create(@Valid @RequestBody ArticleRequest req) {
        Article article = new Article();
        applyRequest(article, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(articleRepository.save(article));
    }

    @PutMapping("/{id}")
    public Article update(@PathVariable Long id, @Valid @RequestBody ArticleRequest req) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบบทความนี้"));
        applyRequest(article, req);
        return articleRepository.save(article);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!articleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบบทความนี้");
        }
        articleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyRequest(Article article, ArticleRequest req) {
        article.setTitle(req.title().trim());
        article.setCategory(req.category().trim());
        article.setPublishedAt(req.publishedAt());
        article.setImage(req.image() == null ? "" : req.image().trim());
        article.setSummary(req.summary().trim());
        article.setContent(req.content() == null ? "" : req.content().trim());
    }
}
