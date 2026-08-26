package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 관광명소 전용 테이블. place 테이블을 종류별로 쪼개면서 병원/숙소({@link HospitalAccommodation})와 분리했다.
 * 도보 제약 ML 태그(is_indoor 등)는 관광명소 CSV에서만 채워진다.
 * 관광공사 API로 들어온 적이 없는 카테고리라 tourism_content_id는 없다.
 */
@Entity
@Table(name = "attractions")
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 고유 ID. */
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

    @Column(name = "category_group_code", length = 16)
    private String categoryGroupCode;

    @Column(name = "category_group_name", length = 64)
    private String categoryGroupName;

    @Column(length = 32)
    private String phone;

    @Column(name = "place_url", length = 512)
    private String placeUrl;

    @Column(name = "primary_type", length = 64)
    private String primaryType;

    @Column(name = "primary_type_name", length = 128)
    private String primaryTypeName;

    @Column(name = "collection_types", length = 256)
    private String collectionTypes;

    /** 실내 여부. */
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

    /** CSV_IMPORT(CSV 통째로 import) / MANUAL(관리 페이지 직접 입력) */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected Attraction() {
    }

    public Attraction(
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
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** ML 태그 CSV 백필 전용. */
    public void applyMlTags(
            String primaryType,
            String primaryTypeName,
            String collectionTypes,
            Boolean isIndoor,
            Boolean isHeatSource,
            Boolean isMassageSpot,
            Integer walkHard,
            Boolean isNa) {
        this.primaryType = primaryType;
        this.primaryTypeName = primaryTypeName;
        this.collectionTypes = collectionTypes;
        this.isIndoor = isIndoor;
        this.isHeatSource = isHeatSource;
        this.isMassageSpot = isMassageSpot;
        this.walkHard = walkHard;
        this.isNa = isNa;
    }

    /**
     * TourAPI/카카오 동기화 시 도보 제약 태그를 자동 분류해 채운다. CSV 백필로 이미 실측값이 들어간
     * 행(is_indoor가 non-null)은 절대 덮어쓰지 않도록 호출하는 쪽(AttractionSyncService)에서
     * is_indoor == null일 때만 호출한다.
     */
    public void applyWalkConstraints(Boolean isIndoor, Boolean isHeatSource, Boolean isMassageSpot, Integer walkHard) {
        this.isIndoor = isIndoor;
        this.isHeatSource = isHeatSource;
        this.isMassageSpot = isMassageSpot;
        this.walkHard = walkHard;
    }

    /** CSV 통째로 import — 기존 행이 없을 때 새로 만드는 용도. */
    public static Attraction fromCsvRow(
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
            Instant syncedAt) {
        return new Attraction(
                placeId, null, placeName, categoryName, addressName, roadAddressName,
                mapX, mapY, null, categoryGroupCode, categoryGroupName, phone, placeUrl,
                "CSV_IMPORT", syncedAt);
    }

    /**
     * 관광공사 TourAPI + 카카오 동기화 시 호출. image_url은 수동으로 override된 경우 건드리지 않고,
     * override되지 않았더라도 이번 응답에 이미지가 없으면 기존 값을 지우지 않고 그대로 둔다 —
     * 값이 있을 때만 교체한다.
     */
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
            String primaryTypeName,
            String source,
            Instant syncedAt) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        if (!this.imageUrlOverridden && imageUrl != null && !imageUrl.isBlank()) {
            this.imageUrl = imageUrl;
        }
        this.categoryGroupCode = categoryGroupCode;
        this.categoryGroupName = categoryGroupName;
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.primaryTypeName = primaryTypeName;
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** 관리 페이지에서 사람이 직접 수정. 항상 전체 덮어쓰고, image는 이후 자동 동기화가 건드리지 않는다. */
    public void applyAdminEdit(
            String placeName,
            String categoryName,
            String categoryGroupName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
            String phone,
            String placeUrl) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.categoryGroupName = categoryGroupName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
        this.phone = phone;
        this.placeUrl = placeUrl;
        this.syncedAt = Instant.now();
    }

    public Long getId() { return id; }
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
    public Boolean getIsIndoor() { return isIndoor; }
    public Boolean getIsHeatSource() { return isHeatSource; }
    public Boolean getIsMassageSpot() { return isMassageSpot; }
    public Integer getWalkHard() { return walkHard; }
    public Boolean getIsNa() { return isNa; }
    public String getSource() { return source; }
    public Instant getSyncedAt() { return syncedAt; }
}
