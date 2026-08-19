package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** treatment 배열의 원소 1개. 날짜(TripDailyRecommendation)당 0~n개, 없는 날짜도 있다. */
@Entity
@Table(name = "trip_daily_treatments")
public class TripDailyTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_recommendation_id", nullable = false)
    private Long dailyRecommendationId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    protected TripDailyTreatment() {
    }

    public TripDailyTreatment(Long dailyRecommendationId, String name, LocalDate treatmentDate) {
        this.dailyRecommendationId = dailyRecommendationId;
        this.name = name;
        this.treatmentDate = treatmentDate;
    }

    public Long getId() { return id; }
    public Long getDailyRecommendationId() { return dailyRecommendationId; }
    public String getName() { return name; }
    public LocalDate getTreatmentDate() { return treatmentDate; }
}
