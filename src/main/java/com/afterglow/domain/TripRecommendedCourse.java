package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** recommended_courses 배열의 원소 1개 = 순위별 추천 코스 후보(여러 날짜의 daily_schedules를 포함하는 최상위). */
@Entity
@Table(name = "trip_recommended_courses")
public class TripRecommendedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private int rank;

    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;

    @Column(name = "total_distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalDistanceKm;

    protected TripRecommendedCourse() {
    }

    public TripRecommendedCourse(
            Long userId, Instant requestedAt, int rank, String courseId, BigDecimal totalDistanceKm) {
        this.userId = userId;
        this.requestedAt = requestedAt;
        this.rank = rank;
        this.courseId = courseId;
        this.totalDistanceKm = totalDistanceKm;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getRequestedAt() { return requestedAt; }
    public int getRank() { return rank; }
    public String getCourseId() { return courseId; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
}
