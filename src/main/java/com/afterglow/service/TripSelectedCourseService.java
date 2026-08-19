package com.afterglow.service;

import com.afterglow.domain.TripDailySchedule;
import com.afterglow.domain.TripRecommendedCourse;
import com.afterglow.domain.TripSelectedCourse;
import com.afterglow.repository.TripCourseTreatmentRepository;
import com.afterglow.repository.TripDailySchedulePlaceRepository;
import com.afterglow.repository.TripDailyScheduleRepository;
import com.afterglow.repository.TripRecommendedCourseRepository;
import com.afterglow.repository.TripSelectedCourseRepository;
import com.afterglow.web.dto.TripSelectedCourseResponse;
import com.afterglow.web.dto.TripSelectedCourseResponse.DailyScheduleResponse;
import com.afterglow.web.dto.TripSelectedCourseResponse.PlaceResponse;
import com.afterglow.web.dto.TripSelectedCourseResponse.StartLocationResponse;
import com.afterglow.web.dto.TripSelectedCourseResponse.TreatmentResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * trip_selected_courses(사용자가 고른 추천 코스 기록)을 조회해 관련 정보를 모두 조인해서 내려준다.
 * trip_recommended_courses / trip_daily_schedules / trip_daily_schedule_places / trip_course_treatments
 * 는 전부 ML 서버가 채우는 테이블이라, 여기서는 조회만 한다(쓰기는 이 서비스 책임이 아님).
 */
@Service
public class TripSelectedCourseService {

    private final TripSelectedCourseRepository selectedCourseRepository;
    private final TripRecommendedCourseRepository recommendedCourseRepository;
    private final TripDailyScheduleRepository dailyScheduleRepository;
    private final TripDailySchedulePlaceRepository dailySchedulePlaceRepository;
    private final TripCourseTreatmentRepository treatmentRepository;

    public TripSelectedCourseService(
            TripSelectedCourseRepository selectedCourseRepository,
            TripRecommendedCourseRepository recommendedCourseRepository,
            TripDailyScheduleRepository dailyScheduleRepository,
            TripDailySchedulePlaceRepository dailySchedulePlaceRepository,
            TripCourseTreatmentRepository treatmentRepository) {
        this.selectedCourseRepository = selectedCourseRepository;
        this.recommendedCourseRepository = recommendedCourseRepository;
        this.dailyScheduleRepository = dailyScheduleRepository;
        this.dailySchedulePlaceRepository = dailySchedulePlaceRepository;
        this.treatmentRepository = treatmentRepository;
    }

    public List<TripSelectedCourseResponse> listByUser(Long userId) {
        return selectedCourseRepository.findByUserIdOrderBySelectedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private TripSelectedCourseResponse toResponse(TripSelectedCourse selection) {
        TripRecommendedCourse course = recommendedCourseRepository
                .findById(selection.getRecommendedCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "선택한 추천 코스를 찾을 수 없습니다: " + selection.getRecommendedCourseId()));

        List<TreatmentResponse> treatments = treatmentRepository
                .findByRecommendedCourseId(course.getId()).stream()
                .map(t -> new TreatmentResponse(t.getName(), t.getTreatmentDate()))
                .toList();

        List<DailyScheduleResponse> schedules = dailyScheduleRepository
                .findByRecommendedCourseIdOrderByScheduleDate(course.getId()).stream()
                .map(this::toScheduleResponse)
                .toList();

        return new TripSelectedCourseResponse(
                selection.getId(),
                selection.getSelectedAt(),
                course.getRank(),
                course.getCourseId(),
                course.getTotalDistanceKm(),
                treatments,
                schedules);
    }

    private DailyScheduleResponse toScheduleResponse(TripDailySchedule schedule) {
        List<PlaceResponse> places = dailySchedulePlaceRepository
                .findByDailyScheduleIdOrderByVisitOrder(schedule.getId()).stream()
                .map(p -> new PlaceResponse(
                        p.getVisitOrder(),
                        p.getPlaceName(),
                        p.getPlaceCategory(),
                        p.getMapX(),
                        p.getMapY(),
                        p.isIndoor(),
                        p.getWalkHard(),
                        p.getDistToPrevKm()))
                .toList();

        return new DailyScheduleResponse(
                schedule.getScheduleDate(),
                new StartLocationResponse(
                        schedule.getStartLocationName(),
                        schedule.getStartLocationMapX(),
                        schedule.getStartLocationMapY()),
                places);
    }
}
