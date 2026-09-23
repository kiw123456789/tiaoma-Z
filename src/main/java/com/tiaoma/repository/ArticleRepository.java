package com.tiaoma.repository;

import com.tiaoma.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findAllByOrderByPublishedAtDesc();
}
