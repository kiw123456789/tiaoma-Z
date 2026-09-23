package com.tiaoma.repository;

import com.tiaoma.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findAllByOrderByPublishedAtDesc();

    List<Article> findByCategoryOrderByPublishedAtDesc(String category);

    @Query("select distinct a.category from Article a order by a.category")
    List<String> findDistinctCategories();
}
