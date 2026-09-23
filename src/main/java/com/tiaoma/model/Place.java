package com.tiaoma.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;

/**
 * สถานที่ท่องเที่ยว — ชื่อฟิลด์ใน JSON ตรงกับ places-data.js ฝั่งหน้าเว็บ
 * (id เป็น String เช่น "doi-inthanon" เพื่อให้ตรงกับ Like.placeId)
 */
@Entity
@Table(name = "places")
public class Place {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String category;

    @JsonProperty("short")
    @Column(name = "short_text", nullable = false, length = 500)
    private String shortText;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "place_descriptions", joinColumns = @JoinColumn(name = "place_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "paragraph", length = 4000)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> description = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "place_highlights", joinColumns = @JoinColumn(name = "place_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "highlight", length = 500)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> highlights = new ArrayList<>();

    @Column(length = 500)
    private String hours;

    @Column(length = 500)
    private String fee;

    @Column(name = "best_time", length = 500)
    private String bestTime;

    @Column(name = "map_query")
    private String mapQuery;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String image;

    private int accent;

    public Place() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getShortText() { return shortText; }
    public void setShortText(String shortText) { this.shortText = shortText; }

    public List<String> getDescription() { return description; }
    public void setDescription(List<String> description) { this.description = description; }

    public List<String> getHighlights() { return highlights; }
    public void setHighlights(List<String> highlights) { this.highlights = highlights; }

    public String getHours() { return hours; }
    public void setHours(String hours) { this.hours = hours; }

    public String getFee() { return fee; }
    public void setFee(String fee) { this.fee = fee; }

    public String getBestTime() { return bestTime; }
    public void setBestTime(String bestTime) { this.bestTime = bestTime; }

    public String getMapQuery() { return mapQuery; }
    public void setMapQuery(String mapQuery) { this.mapQuery = mapQuery; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getAccent() { return accent; }
    public void setAccent(int accent) { this.accent = accent; }
}