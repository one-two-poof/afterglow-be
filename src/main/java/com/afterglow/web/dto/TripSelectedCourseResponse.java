package com.afterglow.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 사용자가 고른(선택한) 추천 코스 1건. trip_selected_courses -> trip_recommended_courses ->
 * trip_daily_schedules -> trip_daily_schedule_places / trip_course_treatments 조인 결과. */
public record TripSelectedCourseResponse(
        Long selectionId,
        Instant selectedAt,
        int rank,
        @JsonProperty("course_id") String courseId,
        @JsonProperty("total_distance_km") BigDecimal totalDistanceKm,
        List<TreatmentResponse> treatment,
        @JsonProperty("daily_schedules") List<DailyScheduleResponse> dailySchedules) {

    public record TreatmentResponse(String name, LocalDate date) {
    }

    public record DailyScheduleResponse(
            LocalDate date,
            @JsonProperty("start_location") StartLocationResponse startLocation,
            List<PlaceResponse> places) {
    }

    public record StartLocationResponse(String name, BigDecimal mapX, BigDecimal mapY) {
    }

    public record PlaceResponse(
            @JsonProperty("visit_order") int visitOrder,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("place_category") String placeCategory,
            BigDecimal mapX,
            BigDecimal mapY,
            @JsonProperty("is_indoor") boolean indoor,
            @JsonProperty("walk_hard") int walkHard,
            @JsonProperty("dist_to_prev_km") BigDecimal distToPrevKm) {
    }
}
