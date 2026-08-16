package com.afterglow.web.dto;

import com.afterglow.domain.CoursePlace;
import com.afterglow.domain.RecommendationResult;
import com.afterglow.domain.RecommendedCourse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecommendationResultResponse(
        Long id,
        String status,
        String message,
        Instant requestedAt,
        List<CourseResponse> courses) {

    public static RecommendationResultResponse from(
            RecommendationResult result, List<CourseResponse> courses) {
        return new RecommendationResultResponse(
                result.getId(), result.getStatus(), result.getMessage(), result.getRequestedAt(), courses);
    }

    public record CourseResponse(
            Long id,
            int rank,
            String courseId,
            BigDecimal predictedRating,
            BigDecimal totalDistanceKm,
            List<PlaceResponse> places) {

        public static CourseResponse from(RecommendedCourse course, List<PlaceResponse> places) {
            return new CourseResponse(
                    course.getId(),
                    course.getRank(),
                    course.getCourseId(),
                    course.getPredictedRating(),
                    course.getTotalDistanceKm(),
                    places);
        }
    }

    public record PlaceResponse(
            Long id,
            int visitOrder,
            String placeName,
            String placeCategory,
            boolean indoor,
            int walkHard) {

        public static PlaceResponse from(CoursePlace place) {
            return new PlaceResponse(
                    place.getId(),
                    place.getVisitOrder(),
                    place.getPlaceName(),
                    place.getPlaceCategory(),
                    place.isIndoor(),
                    place.getWalkHard());
        }
    }
}
