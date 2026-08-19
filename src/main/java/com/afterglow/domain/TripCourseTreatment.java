package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** treatment 배열의 원소 1개. 코스(TripRecommendedCourse)당 0~n개. */
@Entity
@Table(name = "trip_course_treatments")
public class TripCourseTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommended_course_id", nullable = false)
    private Long recommendedCourseId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    protected TripCourseTreatment() {
    }

    public TripCourseTreatment(Long recommendedCourseId, String name, LocalDate treatmentDate) {
        this.recommendedCourseId = recommendedCourseId;
        this.name = name;
        this.treatmentDate = treatmentDate;
    }

    public Long getId() { return id; }
    public Long getRecommendedCourseId() { return recommendedCourseId; }
    public String getName() { return name; }
    public LocalDate getTreatmentDate() { return treatmentDate; }
}
