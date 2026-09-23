package com.tiaoma.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    private String image;

    @Column(nullable = false)
    private String category;

    @Column(name = "published_at", nullable = false)
    private LocalDate publishedAt;

    @Column(length = 20000)
    private String content;

    public Article() {
    }

    public Article(String title, String summary, String image, String category, LocalDate publishedAt) {
        this.title = title;
        this.summary = summary;
        this.image = image;
        this.category = category;
        this.publishedAt = publishedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
