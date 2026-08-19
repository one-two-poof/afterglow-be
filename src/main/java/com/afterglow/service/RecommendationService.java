package com.afterglow.service;

import com.afterglow.domain.CoursePlace;
import com.afterglow.domain.RecommendationResult;
import com.afterglow.domain.RecommendedCourse;
import com.afterglow.repository.CoursePlaceRepository;
import com.afterglow.repository.RecommendationResultRepository;
import com.afterglow.repository.RecommendedCourseRepository;
import com.afterglow.web.dto.RecommendationSubmitRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final RecommendationResultRepository resultRepository;
    private final RecommendedCourseRepository courseRepository;
    private final CoursePlaceRepository placeRepository;

    public RecommendationService(
            RecommendationResultRepository resultRepository,
            RecommendedCourseRepository courseRepository,
            CoursePlaceRepository placeRepository) {
        this.resultRepository = resultRepository;
        this.courseRepository = courseRepository;
        this.placeRepository = placeRepository;
    }

    @Transactional
    public Long save(Long userId, RecommendationSubmitRequest request) {
        RecommendationResult result = resultRepository.save(
                new RecommendationResult(userId, request.status(), request.message(), Instant.now()));

        for (var courseSubmit : request.data().recommendedCourses()) {
            RecommendedCourse course = courseRepository.save(new RecommendedCourse(
                    result.getId(),
                    courseSubmit.rank(),
                    courseSubmit.courseId(),
                    courseSubmit.predictedRating(),
                    courseSubmit.totalDistanceKm()));

            for (var placeSubmit : courseSubmit.places()) {
                placeRepository.save(new CoursePlace(
                        course.getId(),
                        placeSubmit.visitOrder(),
                        placeSubmit.placeName(),
                        placeSubmit.placeCategory(),
                        placeSubmit.isIndoor() != 0,
                        placeSubmit.walkHard()));
            }
        }

        return result.getId();
    }
}
