package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** daily_recommendations 배열의 원소 1개 = 날짜 1건 (출발지 포함, 코스는 별도 테이블). */
@Entity
@Table(name = "trip_daily_recommendations")
public class TripDailyRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "rec_date", nullable = false)
    private LocalDate recDate;

    @Column(name = "start_location_name", nullable = false, length = 256)
    private String startLocationName;

    @Column(name = "start_location_map_x", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLocationMapX;

    @Column(name = "start_location_map_y", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLocationMapY;

    protected TripDailyRecommendation() {
    }

    public TripDailyRecommendation(
            Long userId,
            Instant requestedAt,
            LocalDate recDate,
            String startLocationName,
            BigDecimal startLocationMapX,
            BigDecimal startLocationMapY) {
        this.userId = userId;
        this.requestedAt = requestedAt;
        this.recDate = recDate;
        this.startLocationName = startLocationName;
        this.startLocationMapX = startLocationMapX;
        this.startLocationMapY = startLocationMapY;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getRequestedAt() { return requestedAt; }
    public LocalDate getRecDate() { return recDate; }
    public String getStartLocationName() { return startLocationName; }
    public BigDecimal getStartLocationMapX() { return startLocationMapX; }
    public BigDecimal getStartLocationMapY() { return startLocationMapY; }
}
