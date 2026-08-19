package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** places 배열의 원소 1개 = 일정 안 방문 순서별 장소. */
@Entity
@Table(name = "trip_daily_schedule_places")
public class TripDailySchedulePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_schedule_id", nullable = false)
    private Long dailyScheduleId;

    @Column(name = "visit_order", nullable = false)
    private int visitOrder;

    @Column(name = "place_name", nullable = false, length = 256)
    private String placeName;

    @Column(name = "place_category", nullable = false, length = 64)
    private String placeCategory;

    @Column(name = "map_x", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapX;

    @Column(name = "map_y", nullable = false, precision = 12, scale = 8)
    private BigDecimal mapY;

    @Column(name = "is_indoor", nullable = false)
    private boolean indoor;

    @Column(name = "walk_hard", nullable = false)
    private int walkHard;

    @Column(name = "dist_to_prev_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal distToPrevKm;

    protected TripDailySchedulePlace() {
    }

    public TripDailySchedulePlace(
            Long dailyScheduleId,
            int visitOrder,
            String placeName,
            String placeCategory,
            BigDecimal mapX,
            BigDecimal mapY,
            boolean indoor,
            int walkHard,
            BigDecimal distToPrevKm) {
        this.dailyScheduleId = dailyScheduleId;
        this.visitOrder = visitOrder;
        this.placeName = placeName;
        this.placeCategory = placeCategory;
        this.mapX = mapX;
        this.mapY = mapY;
        this.indoor = indoor;
        this.walkHard = walkHard;
        this.distToPrevKm = distToPrevKm;
    }

    public Long getId() { return id; }
    public Long getDailyScheduleId() { return dailyScheduleId; }
    public int getVisitOrder() { return visitOrder; }
    public String getPlaceName() { return placeName; }
    public String getPlaceCategory() { return placeCategory; }
    public BigDecimal getMapX() { return mapX; }
    public BigDecimal getMapY() { return mapY; }
    public boolean isIndoor() { return indoor; }
    public int getWalkHard() { return walkHard; }
    public BigDecimal getDistToPrevKm() { return distToPrevKm; }
}
