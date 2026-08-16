package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hospital")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    /** TOURISM_API+KAKAO(둘 다 매칭) / TOURISM_API(관광공사만) / KAKAO(카카오만) / MANUAL(관리 페이지 직접 입력) */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected Hospital() {
    }

    public Hospital(
            String placeId,
            String tourismContentId,
            String placeName,
            String categoryName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl,
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
        this.source = source;
        this.syncedAt = syncedAt;
    }

    /** Notion 등에서 이미지 수동 입력. 이후 updateFromSync가 imageUrl을 덮어쓰지 않는다. */
    public void overrideImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
    }

    /** 관리 페이지에서 사람이 직접 수정. 항상 전체 덮어쓰고, image는 이후 자동 동기화가 건드리지 않는다. */
    public void applyAdminEdit(
            String placeName,
            String categoryName,
            String addressName,
            String roadAddressName,
            BigDecimal mapX,
            BigDecimal mapY,
            String imageUrl) {
        this.placeName = placeName;
        this.categoryName = categoryName;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.mapX = mapX;
        this.mapY = mapY;
        this.imageUrl = imageUrl;
        this.imageUrlOverridden = true;
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
    public String getSource() { return source; }
    public Instant getSyncedAt() { return syncedAt; }
}
