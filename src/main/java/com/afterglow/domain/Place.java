package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** 병원/숙소/관광명소 등을 통합해서 담는 장소 테이블. 종류 구분은 placeType으로 명시적으로 한다. */
@Entity
@Table(name = "place")
public class Place {

    public enum PlaceType {
        HOSPITAL, ACCOMMODATION, ATTRACTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 16)
    private PlaceType placeType;

    /** 카카오 고유 ID. 관광공사에만 있고 카카오 매칭이 안 된 행은 null일 수 있다. */
    @Column(name = "place_id", unique = true, length = 64)
    private String placeId;

    /** 관광공사 contentId. 카카오 매칭이 안 됐을 때 재동기화 시 같은 행을 찾기 위한 키. */
    @Column(name = "tourism_content_id", unique = true, length = 32)
    private String tourismContentId;

    @Column(name = "place_name", nullable = false, length = 256)
    private String placeName;

    @Column(name = "category_name", length = 256)
    private String categoryName;

    @Column(name = "address_name", length = 512)
    private String addressName;

    @Column(name = "road_address_name", length = 512)
    private String roadAddressName;

    @Column(name = "map_x", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapX;

    @Column(name = "map_y", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapY;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "image_url_overridden", nullable = false)
    private boolean imageUrlOverridden;

    // --- 카카오 API가 이미 주는 필드 (동기화/CSV import 때 자동으로 채워짐) ---

    @Column(name = "category_group_code", length = 16)
    private String categoryGroupCode;

    @Column(name = "category_group_name", length = 64)
    private String categoryGroupName;

    @Column(length = 32)
    private String phone;

    @Column(name = "place_url", length = 512)
    private String placeUrl;

    // --- 카카오/관광공사 어디에도 없는, CSV로만 채워지는 ML 태그 필드 ---

    @Column(name = "primary_type", length = 64)
    private String primaryType;

    @Column(name = "primary_type_name", length = 128)
    private String primaryTypeName;

    /** "skin_treatment_hospital|hospital" 처럼 "|"로 구분된 다중 태그 원문 그대로 저장 */
    @Column(name = "collection_types", length = 256)
    private String collectionTypes;

    @Column(name = "skin_treatment_confidence", length = 16)
    private String skinTreatmentConfidence;

    /** "리프팅|보톡스|울쎄라|피부레이저|필러" 처럼 "|"로 구분된 시술 키워드 원문 그대로 저장 */
    @Column(name = "skin_treatment_signals", length = 512)
    private String skinTreatmentSignals;

    // --- 코스 추천용 도보 제약 태그 (CSV로만 채워짐, 카카오/관광공사 어디에도 없음) ---

    /** 실내 여부. CSV에 없는 카테고리(병원 등)는 null. */
    @Column(name = "is_indoor")
    private Boolean isIndoor;

    /** 폭염 시 피해야 할 발열원(야외 조리 등) 여부. */
    @Column(name = "is_heat_source")
    private Boolean isHeatSource;

    /** 마사지/안마 등 시술 후 방문에 적합한 장소 여부. */
    @Column(name = "is_massage_spot")
    private Boolean isMassageSpot;

    /** 도보 난이도 등급(낮을수록 쉬움). CSV 원본은 1~5 정수. */
    @Column(name = "walk_hard")
    private Integer walkHard;

    /** ML 라벨링 단계에서 결측치로 표시된 행인지 여부. */
    @Column(name = "is_na")
    private Boolean isNa;

    /**
     * TOURISM_API+KAKAO(병원, 관광공사+카카오 둘 다 매칭) / TOURISM_API(병원, 관광공사만) /
     * KAKAO(병원, 카카오만) / CSV_IMPORT(숙소 등 CSV 통째로 import) / MANUAL(관리 페이지 직접 입력)
     */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected Place() {
    }

    public Place(
            String placeId,
            String tourismContentId,
            String placeName,
            String categoryName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            String categoryGroupCode,
            String categoryGroupName,
            String phone,
            String placeUrl,
            PlaceType placeType,
            String source,
            Instant syncedAt) {
        this.placeId = placeId;
        this.tourismContentId = tourismContentId;
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = false;
        this.categoryGroupCode = categoryGroupCode;
        this.categoryGroupName = categoryGroupName;
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.placeType = placeType;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** API 재동기화 시 호출. image_url은 수동으로 override된 경우 건드리지 않는다. */
    public void updateFromSync(
            String placeName,
            String categoryName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            String categoryGroupCode,
            String categoryGroupName,
            String phone,
            String placeUrl,
            String source,
            Instant syncedAt) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        if (!this.imageUrlOverridden) {
            this.imageUrl = imageUrl;
        }
        this.categoryGroupCode = categoryGroupCode;
        this.categoryGroupName = categoryGroupName;
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** Notion 등에서 이미지 수동 입력. 이후 updateFromSync가 imageUrl을 덮어쓰지 않는다. */
    public void overrideImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
    }

    /**
     * ML 태그 CSV 백필 전용. 카카오/관광공사 동기화는 이 필드들을 절대 건드리지 않는다.
     * 도보 제약 5종(isIndoor 등)은 gangnam_seocho_places_with_constraints.csv에만 있고
     * 병원 CSV(gangnam_seocho_hospital_db.csv)에는 없는 컬럼이라 null이 그대로 들어올 수 있다.
     */
    public void applyMlTags(
            String primaryType,
            String primaryTypeName,
            String collectionTypes,
            String skinTreatmentConfidence,
            String skinTreatmentSignals,
            Boolean isIndoor,
            Boolean isHeatSource,
            Boolean isMassageSpot,
            Integer walkHard,
            Boolean isNa) {
        this.primaryType = primaryType;
        this.primaryTypeName = primaryTypeName;
        this.collectionTypes = collectionTypes;
        this.skinTreatmentConfidence = skinTreatmentConfidence;
        this.skinTreatmentSignals = skinTreatmentSignals;
        this.isIndoor = isIndoor;
        this.isHeatSource = isHeatSource;
        this.isMassageSpot = isMassageSpot;
        this.walkHard = walkHard;
        this.isNa = isNa;
    }

    /** CSV 통째로 import (예: 숙소) — 기존 행이 없을 때 새로 만드는 용도. */
    public static Place fromCsvRow(
            String placeId,
            String placeName,
            String categoryName,
            String categoryGroupCode,
            String categoryGroupName,
            String phone,
            String addressName,
            String roadAddressName,
            String placeUrl,
            BigDecimal mapX,
            BigDecimal mapY,
            PlaceType placeType,
            Instant syncedAt) {
        return new Place(
                placeId, null, placeName, categoryName, addressName, roadAddressName,
                mapX, mapY, null, categoryGroupCode, categoryGroupName, phone, placeUrl,
                placeType, "CSV_IMPORT", syncedAt);
    }

    /** 관리 페이지에서 사람이 직접 수정. 항상 전체 덮어쓰고, image는 이후 자동 동기화가 건드리지 않는다. */
    public void applyAdminEdit(
            String placeName,
            String categoryName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            PlaceType placeType) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
        if (placeType != null) {
            this.placeType = placeType;
        }
        this.syncedAt = Instant.now();
    }

    public Long getId() { return id; }
    public PlaceType getPlaceType() { return placeType; }
    public String getPlaceId() { return placeId; }
    public String getTourismContentId() { return tourismContentId; }
    public String getPlaceName() { return placeName; }
    public String getCategoryName() { return categoryName; }
    public String getAddressName() { return addressName; }
    public String getRoadAddressName() { return roadAddressName; }
    public BigDecimal getMapX() { return mapX; }
    public BigDecimal getMapY() { return mapY; }
    public String getImageUrl() { return imageUrl; }
    public boolean isImageUrlOverridden() { return imageUrlOverridden; }
    public String getCategoryGroupCode() { return categoryGroupCode; }
    public String getCategoryGroupName() { return categoryGroupName; }
    public String getPhone() { return phone; }
    public String getPlaceUrl() { return placeUrl; }
    public String getPrimaryType() { return primaryType; }
    public String getPrimaryTypeName() { return primaryTypeName; }
    public String getCollectionTypes() { return collectionTypes; }
    public String getSkinTreatmentConfidence() { return skinTreatmentConfidence; }
    public String getSkinTreatmentSignals() { return skinTreatmentSignals; }
    public Boolean getIsIndoor() { return isIndoor; }
    public Boolean getIsHeatSource() { return isHeatSource; }
    public Boolean getIsMassageSpot() { return isMassageSpot; }
    public Integer getWalkHard() { return walkHard; }
    public Boolean getIsNa() { return isNa; }
    public String getSource() { return source; }
    public Instant getSyncedAt() { return syncedAt; }
}
