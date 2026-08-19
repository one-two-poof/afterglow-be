package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** daily_schedules 배열의 원소 1개 = 코스 안 날짜 1건 (출발지 포함, 장소는 별도 테이블). */
@Entity
@Table(name = "trip_daily_schedules")
public class TripDailySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommended_course_id", nullable = false)
    private Long recommendedCourseId;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "start_location_name", nullable = false, length = 256)
    private String startLocationName;

    @Column(name = "start_location_map_x", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLocationMapX;

    @Column(name = "start_location_map_y", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLocationMapY;

    protected TripDailySchedule() {
    }

    public TripDailySchedule(
            Long recommendedCourseId,
            LocalDate scheduleDate,
            String startLocationName,
            BigDecimal startLocationMapX,
            BigDecimal startLocationMapY) {
        this.recommendedCourseId = recommendedCourseId;
        this.scheduleDate = scheduleDate;
        this.startLocationName = startLocationName;
        this.startLocationMapX = startLocationMapX;
        this.startLocationMapY = startLocationMapY;
    }

    public Long getId() { return id; }
    public Long getRecommendedCourseId() { return recommendedCourseId; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public String getStartLocationName() { return startLocationName; }
    public BigDecimal getStartLocationMapX() { return startLocationMapX; }
    public BigDecimal getStartLocationMapY() { return startLocationMapY; }
}
