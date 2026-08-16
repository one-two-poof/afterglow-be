package com.afterglow.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationSubmitRequest(
        String status,
        String message,
        RecommendationData data) {

    public record RecommendationData(
            @JsonProperty("recommended_courses") List<CourseSubmit> recommendedCourses) {
    }

    public record CourseSubmit(
            int rank,
            @JsonProperty("course_id") String courseId,
            @JsonProperty("predicted_rating") BigDecimal predictedRating,
            @JsonProperty("total_distance_km") BigDecimal totalDistanceKm,
            List<PlaceSubmit> places) {
    }

    public record PlaceSubmit(
            @JsonProperty("visit_order") int visitOrder,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("place_category") String placeCategory,
            @JsonProperty("is_indoor") int isIndoor,
            @JsonProperty("walk_hard") int walkHard) {
    }
}
