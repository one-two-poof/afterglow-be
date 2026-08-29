package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * {@link Attraction}/{@link HospitalAccommodation}의 place_name·category_name을 로케일별로 보관한다.
 * place_id는 placeType에 따라 attractions.id 또는 hospitals_accommodations.id를 가리키는 논리적 참조다
 * (두 테이블 중 하나를 가리켜야 해서 DB FK로는 표현할 수 없다 — placeType으로 어느 테이블인지 구분).
 * ko(원본)는 별도 row 없이 원본 테이블 값을 그대로 쓰고, 이 테이블은 ja/en처럼 원본이 아닌 로케일만 담는다.
 *
 * <p>placeName/categoryName은 최초 채워질 때 한 번만 값이 들어가고(다른 place_* 엔티티들과 같은
 * "write-once, then protect" 패턴), 이후 재동기화/백필은 이미 값이 있는 필드를 덮어쓰지 않는다.
 * overridden=true면 관리자가 손으로 고친 것이므로 자동 동기화/백필이 아예 건드리지 않는다.
 */
@Entity
@Table(
        name = "place_translations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"place_type", "place_id", "locale"}))
public class PlaceTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 16)
    private PlaceType placeType;

    /** attractions.id 또는 hospitals_accommodations.id (placeType으로 구분) */
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    /** 'ja' | 'en' */
    @Column(nullable = false, length = 8)
    private String locale;

    @Column(name = "place_name", length = 256)
    private String placeName;

    @Column(name = "category_name", length = 256)
    private String categoryName;

    /** TOURAPI_JPN / TOURAPI_ENG / MEDICALTOURISM_JPN / MEDICALTOURISM_ENG / SEED_SAMPLE / MANUAL */
    @Column(length = 32)
    private String source;

    /** 관리자가 손으로 고친 필드가 하나라도 있으면 true — 이후 자동 동기화/백필이 이 행 전체를 건드리지 않는다. */
    @Column(nullable = false)
    private boolean overridden;

    @Column(name = "translated_at")
    private Instant translatedAt;

    protected PlaceTranslation() {
    }

    public PlaceTranslation(PlaceType placeType, Long placeId, String locale) {
        this.placeType = placeType;
        this.placeId = placeId;
        this.locale = locale;
        this.overridden = false;
    }

    public boolean isPlaceNameFilled() {
        return placeName != null && !placeName.isBlank();
    }

    public boolean isCategoryNameFilled() {
        return categoryName != null && !categoryName.isBlank();
    }

    /** 최초 1회만 채워진다 — 이미 값이 있거나 관리자가 override한 행은 무시. */
    public void applyPlaceName(String placeName, String source, Instant translatedAt) {
        if (overridden || isPlaceNameFilled() || placeName == null || placeName.isBlank()) {
            return;
        }
        this.placeName = placeName;
        this.source = source;
        this.translatedAt = translatedAt;
    }

    /** 최초 1회만 채워진다 — 이미 값이 있거나 관리자가 override한 행은 무시. */
    public void applyCategoryName(String categoryName, String source, Instant translatedAt) {
        if (overridden || isCategoryNameFilled() || categoryName == null || categoryName.isBlank()) {
            return;
        }
        this.categoryName = categoryName;
        if (this.source == null) {
            this.source = source;
        }
        this.translatedAt = translatedAt;
    }

    /** 관리 페이지에서 사람이 직접 수정. 이후 자동 동기화/백필이 이 행을 건드리지 않는다. */
    public void applyAdminEdit(String placeName, String categoryName) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.source = "MANUAL";
        this.overridden = true;
        this.translatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public PlaceType getPlaceType() { return placeType; }
    public Long getPlaceId() { return placeId; }
    public String getLocale() { return locale; }
    public String getPlaceName() { return placeName; }
    public String getCategoryName() { return categoryName; }
    public String getSource() { return source; }
    public boolean isOverridden() { return overridden; }
    public Instant getTranslatedAt() { return translatedAt; }
}
