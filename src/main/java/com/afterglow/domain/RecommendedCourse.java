package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "recommended_courses")
public class RecommendedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_result_id", nullable = false)
    private Long recommendationResultId;

    @Column(nullable = false)
    private int rank;

    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;

    @Column(name = "predicted_rating", nullable = false, precision = 4, scale = 2)
    private BigDecimal predictedRating;

    @Column(name = "total_distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal totalDistanceKm;

    protected RecommendedCourse() {
    }

    public RecommendedCourse(
            Long recommendationResultId,
            int rank,
            String courseId,
            BigDecimal predictedRating,
            BigDecimal totalDistanceKm) {
        this.recommendationResultId = recommendationResultId;
        this.rank = rank;
        this.courseId = courseId;
        this.predictedRating = predictedRating;
        this.totalDistanceKm = totalDistanceKm;
    }

    public Long getId() { return id; }
    public Long getRecommendationResultId() { return recommendationResultId; }
    public int getRank() { return rank; }
    public String getCourseId() { return courseId; }
    public BigDecimal getPredictedRating() { return predictedRating; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
}
