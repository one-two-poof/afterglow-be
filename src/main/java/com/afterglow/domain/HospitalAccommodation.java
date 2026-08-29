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

/**
 * 병원/숙소 전용 테이블. place 테이블을 종류별로 쪼개면서 관광명소({@link Attraction})와 분리했다.
 * 피부시술 ML 태그(skin_treatment_*)는 병원 데이터에서만 채워지고 숙소는 항상 null.
 */
@Entity
@Table(name = "hospitals_accommodations")
public class HospitalAccommodation {

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

    @Column(name = "map_x", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapX;

    @Column(name = "map_y", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapY;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "image_url_overridden", nullable = false)
    private boolean imageUrlOverridden;

    @Column(length = 32)
    private String phone;

    @Column(name = "place_url", length = 512)
    private String placeUrl;

    @Column(name = "primary_type_name", length = 128)
    private String primaryTypeName;

    @Column(name = "skin_treatment_confidence", length = 16)
    private String skinTreatmentConfidence;

    /** "리프팅|보톡스|울쎄라|피부레이저|필러" 처럼 "|"로 구분된 시술 키워드 원문 그대로 저장 */
    @Column(name = "skin_treatment_signals", length = 512)
    private String skinTreatmentSignals;

    /**
     * TOURISM_API+KAKAO(병원, 관광공사+카카오 둘 다 매칭) / TOURISM_API(병원, 관광공사만) /
     * KAKAO(병원, 카카오만) / MANUAL(관리 페이지 직접 입력)
     */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected HospitalAccommodation() {
    }

    public HospitalAccommodation(
            String placeId,
            String tourismContentId,
            String placeName,
            String categoryName,
            String addressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
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
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = false;
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.placeType = placeType;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /**
     * 병원 동기화 시 호출. image_url은 수동으로 override된 경우 건드리지 않고,
     * override되지 않았더라도 이번 응답에 이미지가 없으면(관광공사 상당수가 그렇다)
     * 기존 값을 지우지 않고 그대로 둔다 — 값이 있을 때만 교체한다.
     * categoryName은 최초 생성 시에만 값이 들어가고, 이후 재동기화에서는
     * 절대 덮어쓰지 않는다(수동 분류/보정이 이 필드에 쌓일 수 있어서).
     */
    public void updateFromSync(
            String placeName,
            String addressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            String phone,
            String placeUrl,
            String source,
            Instant syncedAt) {
        this.placeName = placeName;
        this.addressName = addressName;
        this.mapX = mapX;
        this.mapY = mapY;
        if (!this.imageUrlOverridden && imageUrl != null && !imageUrl.isBlank()) {
            this.imageUrl = imageUrl;
        }
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** 카카오/관광공사 동기화는 이 필드들을 절대 건드리지 않는다. */
    public void applyMlTags(
            String primaryTypeName,
            String skinTreatmentConfidence,
            String skinTreatmentSignals) {
        this.primaryTypeName = primaryTypeName;
        this.skinTreatmentConfidence = skinTreatmentConfidence;
        this.skinTreatmentSignals = skinTreatmentSignals;
    }

    /** 관리 페이지에서 사람이 직접 수정. 항상 전체 덮어쓰고, image는 이후 자동 동기화가 건드리지 않는다. */
    public void applyAdminEdit(
            String placeName,
            String categoryName,
            String addressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            String phone,
            String placeUrl,
            PlaceType placeType) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
        this.phone = phone;
        this.placeUrl = placeUrl;
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
    public BigDecimal getMapX() { return mapX; }
    public BigDecimal getMapY() { return mapY; }
    public String getImageUrl() { return imageUrl; }
    public boolean isImageUrlOverridden() { return imageUrlOverridden; }
    public String getPhone() { return phone; }
    public String getPlaceUrl() { return placeUrl; }
    public String getPrimaryTypeName() { return primaryTypeName; }
    public String getSkinTreatmentConfidence() { return skinTreatmentConfidence; }
    public String getSkinTreatmentSignals() { return skinTreatmentSignals; }
    public String getSource() { return source; }
    public Instant getSyncedAt() { return syncedAt; }
}
