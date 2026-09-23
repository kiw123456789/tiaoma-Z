package com.tiaoma.controller;

import com.tiaoma.model.Article;
import com.tiaoma.model.Place;
import com.tiaoma.repository.ArticleRepository;
import com.tiaoma.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * สร้าง sitemap.xml อัตโนมัติจากสถานที่และบทความที่มีอยู่จริงในฐานข้อมูล
 * ช่วยให้ Google เก็บหน้ารายละเอียดทุกหน้าได้ครบ โดยไม่ต้องมาแก้ไฟล์เองทุกครั้งที่เพิ่มเนื้อหา
 */
@RestController
public class SitemapController {

    private static final String[] STATIC_PAGES = {
            "", "places.html", "articles.html", "about.html"
    };

    private final PlaceRepository placeRepository;
    private final ArticleRepository articleRepository;
    private final String baseUrl;

    public SitemapController(PlaceRepository placeRepository,
                             ArticleRepository articleRepository,
                             @Value("${app.public-base-url}") String baseUrl) {
        this.placeRepository = placeRepository;
        this.articleRepository = articleRepository;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDate.now().toString();

        for (String page : STATIC_PAGES) {
            appendUrl(xml, baseUrl + "/" + page, today, page.isEmpty() ? "1.0" : "0.8");
        }

        for (Place place : placeRepository.findAll()) {
            // ไม่ escape ตรงนี้ เพราะ appendUrl escape ให้ทั้ง URL อยู่แล้ว (escape ซ้ำจะได้ &amp;amp;)
            appendUrl(xml, baseUrl + "/place-detail.html?id=" + place.getId(), today, "0.7");
        }

        for (Article article : articleRepository.findAllByOrderByPublishedAtDesc()) {
            String lastmod = article.getPublishedAt() == null ? today : article.getPublishedAt().toString();
            appendUrl(xml, baseUrl + "/article-detail.html?id=" + article.getId(), lastmod, "0.6");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod, String priority) {
        xml.append("  <url>\n")
           .append("    <loc>").append(escape(loc)).append("</loc>\n")
           .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
           .append("    <priority>").append(priority).append("</priority>\n")
           .append("  </url>\n");
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
