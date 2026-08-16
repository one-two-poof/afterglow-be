package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_places")
public class CoursePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommended_course_id", nullable = false)
    private Long recommendedCourseId;

    @Column(name = "visit_order", nullable = false)
    private int visitOrder;

    @Column(name = "place_name", nullable = false, length = 256)
    private String placeName;

    @Column(name = "place_category", nullable = false, length = 64)
    private String placeCategory;

    @Column(name = "is_indoor", nullable = false)
    private boolean indoor;

    @Column(name = "walk_hard", nullable = false)
    private int walkHard;

    protected CoursePlace() {
    }

    public CoursePlace(
            Long recommendedCourseId,
            int visitOrder,
            String placeName,
            String placeCategory,
            boolean indoor,
            int walkHard) {
        this.recommendedCourseId = recommendedCourseId;
        this.visitOrder = visitOrder;
        this.placeName = placeName;
        this.placeCategory = placeCategory;
        this.indoor = indoor;
        this.walkHard = walkHard;
    }

    public Long getId() { return id; }
    public Long getRecommendedCourseId() { return recommendedCourseId; }
    public int getVisitOrder() { return visitOrder; }
    public String getPlaceName() { return placeName; }
    public String getPlaceCategory() { return placeCategory; }
    public boolean isIndoor() { return indoor; }
    public int getWalkHard() { return walkHard; }
}
