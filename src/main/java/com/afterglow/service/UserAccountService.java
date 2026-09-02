package com.afterglow.service;

import com.afterglow.domain.RecommendationResult;
import com.afterglow.domain.RecommendedCourse;
import com.afterglow.domain.TripDailySchedule;
import com.afterglow.domain.TripRecommendedCourse;
import com.afterglow.repository.CoursePlaceRepository;
import com.afterglow.repository.RecommendationResultRepository;
import com.afterglow.repository.RecommendedCourseRepository;
import com.afterglow.repository.TripCourseTreatmentRepository;
import com.afterglow.repository.TripDailySchedulePlaceRepository;
import com.afterglow.repository.TripDailyScheduleRepository;
import com.afterglow.repository.TripRecommendedCourseRepository;
import com.afterglow.repository.TripSelectedCourseRepository;
import com.afterglow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴(계정 완전 삭제) 처리.
 * users 테이블엔 FK 제약이 걸려있지 않아(schema.sql 주석 참고 — Spring/FastAPI가 독립적으로 insert하기
 * 위한 설계), user_id로 연결된 자식 테이블들을 애플리케이션 레벨에서 직접 하위→상위 순으로 지운 뒤
 * 마지막에 User 행을 지운다.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final RecommendationResultRepository recommendationResultRepository;
    private final RecommendedCourseRepository recommendedCourseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final TripSelectedCourseRepository tripSelectedCourseRepository;
    private final TripRecommendedCourseRepository tripRecommendedCourseRepository;
    private final TripDailyScheduleRepository tripDailyScheduleRepository;
    private final TripDailySchedulePlaceRepository tripDailySchedulePlaceRepository;
    private final TripCourseTreatmentRepository tripCourseTreatmentRepository;

    public UserAccountService(
            UserRepository userRepository,
            RecommendationResultRepository recommendationResultRepository,
            RecommendedCourseRepository recommendedCourseRepository,
            CoursePlaceRepository coursePlaceRepository,
            TripSelectedCourseRepository tripSelectedCourseRepository,
            TripRecommendedCourseRepository tripRecommendedCourseRepository,
            TripDailyScheduleRepository tripDailyScheduleRepository,
            TripDailySchedulePlaceRepository tripDailySchedulePlaceRepository,
            TripCourseTreatmentRepository tripCourseTreatmentRepository) {
        this.userRepository = userRepository;
        this.recommendationResultRepository = recommendationResultRepository;
        this.recommendedCourseRepository = recommendedCourseRepository;
        this.coursePlaceRepository = coursePlaceRepository;
        this.tripSelectedCourseRepository = tripSelectedCourseRepository;
        this.tripRecommendedCourseRepository = tripRecommendedCourseRepository;
        this.tripDailyScheduleRepository = tripDailyScheduleRepository;
        this.tripDailySchedulePlaceRepository = tripDailySchedulePlaceRepository;
        this.tripCourseTreatmentRepository = tripCourseTreatmentRepository;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        deleteRecommendationHistory(userId);
        deleteTripHistory(userId);
        userRepository.deleteById(userId);
    }

    private void deleteRecommendationHistory(Long userId) {
        var results = recommendationResultRepository.findByUserIdOrderByRequestedAtDesc(userId);
        for (RecommendationResult result : results) {
            var courses = recommendedCourseRepository
                    .findByRecommendationResultIdOrderByRank(result.getId());
            for (RecommendedCourse course : courses) {
                coursePlaceRepository.deleteAll(
                        coursePlaceRepository.findByRecommendedCourseIdOrderByVisitOrder(course.getId()));
            }
            recommendedCourseRepository.deleteAll(courses);
        }
        recommendationResultRepository.deleteAll(results);
    }

    private void deleteTripHistory(Long userId) {
        tripSelectedCourseRepository.deleteAll(
                tripSelectedCourseRepository.findByUserIdOrderBySelectedAtDesc(userId));

        var recommendedCourses = tripRecommendedCourseRepository.findByUserIdOrderByRequestedAtDesc(userId);
        for (TripRecommendedCourse course : recommendedCourses) {
            var schedules = tripDailyScheduleRepository
                    .findByRecommendedCourseIdOrderByScheduleDate(course.getId());
            for (TripDailySchedule schedule : schedules) {
                tripDailySchedulePlaceRepository.deleteAll(
                        tripDailySchedulePlaceRepository.findByDailyScheduleIdOrderByVisitOrder(schedule.getId()));
            }
            tripDailyScheduleRepository.deleteAll(schedules);
            tripCourseTreatmentRepository.deleteAll(
                    tripCourseTreatmentRepository.findByRecommendedCourseId(course.getId()));
        }
        tripRecommendedCourseRepository.deleteAll(recommendedCourses);
    }
}
