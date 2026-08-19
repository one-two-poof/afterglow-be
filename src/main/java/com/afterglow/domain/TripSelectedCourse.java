package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자가 추천받은 후보(TripRecommendedCourse) 중 하나를 고른 기록.
 * ML 피드백 데이터 성격이라 재선택 시 덮어쓰지 않고 매번 새 행으로 쌓는다 —
 * 한 사용자가 여러 번 추천을 받을 수 있고, 그때마다 무엇을 골랐는지가 전부 유효한 신호다.
 */
@Entity
@Table(name = "trip_selected_courses")
public class TripSelectedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recommended_course_id", nullable = false)
    private Long recommendedCourseId;

    @Column(name = "selected_at", nullable = false, updatable = false)
    private Instant selectedAt;

    protected TripSelectedCourse() {
    }

    public TripSelectedCourse(Long userId, Long recommendedCourseId, Instant selectedAt) {
        this.userId = userId;
        this.recommendedCourseId = recommendedCourseId;
        this.selectedAt = selectedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRecommendedCourseId() { return recommendedCourseId; }
    public Instant getSelectedAt() { return selectedAt; }
}
