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
 * {@link Attraction}/{@link HospitalAccommodation}의 소개글(overview)·이미지·운영정보를 담는
 * 별도 테이블. place_id는 placeType에 따라 attractions.id 또는 hospitals_accommodations.id를
 * 가리키는 논리적 참조다({@link PlaceTranslation}과 같은 이유로 DB FK 불가).
 *
 * <p>tourism_content_id가 있는 행(TourAPI/의료관광 API로 매칭된 행)에만 채워질 수 있고, 카카오
 * 단독 소스 행은 이 테이블에 행 자체가 없다. 값은 최초 채워질 때 한 번만 들어가고(write-once,
 * {@link PlaceTranslation}과 같은 패턴), 이후 재백필은 덮어쓰지 않는다 — 소개글/운영정보는 자주
 * 바뀌지 않고, 매번 다시 부르면 API 호출량만 낭비된다. overridden=true(관리자 수동 수정)면 자동
 * 백필이 이 행을 전혀 건드리지 않는다.
 */
@Entity
@Table(
        name = "place_details",
        uniqueConstraints = @UniqueConstraint(columnNames = {"place_type", "place_id"}))
public class PlaceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", nullable = false, length = 16)
    private PlaceType placeType;

    /** attractions.id 또는 hospitals_accommodations.id (placeType으로 구분) */
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(columnDefinition = "TEXT")
    private String overview;

    /**
     * "|"로 구분된 이미지 URL 목록 (skin_treatment_signals와 같은 컨벤션). detailImage2가 최대 20장까지
     * 주는데 URL이 길면 쉽게 2048자를 넘어서 VARCHAR 대신 TEXT로 잡는다(overview/extra_info와 동일).
     */
    @Column(columnDefinition = "TEXT")
    private String images;

    /** 타입별 운영정보를 담은 JSON 문자열 (예: 숙소의 체크인/체크아웃/부대시설, 병원의 진료과목/언어지원 등) */
    @Column(name = "extra_info", columnDefinition = "TEXT")
    private String extraInfo;

    /** MEDICALTOURISM(병원) / TOURAPI(숙소·관광명소) / MANUAL(관리자 직접 입력) */
    @Column(length = 32)
    private String source;

    /** 관리자가 손으로 고쳤으면 true — 이후 자동 백필이 이 행 전체를 건드리지 않는다. */
    @Column(nullable = false)
    private boolean overridden;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    protected PlaceDetail() {
    }

    public PlaceDetail(PlaceType placeType, Long placeId) {
        this.placeType = placeType;
        this.placeId = placeId;
        this.overridden = false;
    }

    public boolean isFilled() {
        return overview != null && !overview.isBlank();
    }

    /** 최초 1회만 채워진다 — 이미 값이 있거나 관리자가 override한 행은 무시. */
    public void applyDetail(String overview, String images, String extraInfo, String source, Instant fetchedAt) {
        if (overridden || isFilled() || overview == null || overview.isBlank()) {
            return;
        }
        this.overview = overview;
        this.images = images;
        this.extraInfo = extraInfo;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() { return id; }
    public PlaceType getPlaceType() { return placeType; }
    public Long getPlaceId() { return placeId; }
    public String getOverview() { return overview; }
    public String getImages() { return images; }
    public String getExtraInfo() { return extraInfo; }
    public String getSource() { return source; }
    public boolean isOverridden() { return overridden; }
    public Instant getFetchedAt() { return fetchedAt; }
}
