package com.afterglow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.afterglow.domain.CoursePlace;
import com.afterglow.domain.RecommendationResult;
import com.afterglow.domain.RecommendedCourse;
import com.afterglow.domain.TripCourseTreatment;
import com.afterglow.domain.TripDailySchedule;
import com.afterglow.domain.TripDailySchedulePlace;
import com.afterglow.domain.TripRecommendedCourse;
import com.afterglow.domain.TripSelectedCourse;
import com.afterglow.domain.User;
import com.afterglow.repository.CoursePlaceRepository;
import com.afterglow.repository.RecommendationResultRepository;
import com.afterglow.repository.RecommendedCourseRepository;
import com.afterglow.repository.TripCourseTreatmentRepository;
import com.afterglow.repository.TripDailySchedulePlaceRepository;
import com.afterglow.repository.TripDailyScheduleRepository;
import com.afterglow.repository.TripRecommendedCourseRepository;
import com.afterglow.repository.TripSelectedCourseRepository;
import com.afterglow.repository.UserRepository;
import com.afterglow.security.JwtProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DELETE /api/auth/me(회원 탈퇴)가 users 행뿐 아니라 user_id로 연결된 추천/선택 이력 전체를
 * 실제로 지우는지 검증한다. schema.sql 설계상 이 테이블들엔 FK 제약이 없어(Spring/FastAPI가 독립적으로
 * insert하기 위함) DB 레벨 ON DELETE CASCADE에 기댈 수 없고, UserAccountService가 애플리케이션
 * 레벨에서 직접 정리한다 — 그 정리 로직이 빠짐없이 도는지가 이 테스트의 핵심.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerAccountDeletionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RecommendationResultRepository recommendationResultRepository;
    @Autowired private RecommendedCourseRepository recommendedCourseRepository;
    @Autowired private CoursePlaceRepository coursePlaceRepository;
    @Autowired private TripSelectedCourseRepository tripSelectedCourseRepository;
    @Autowired private TripRecommendedCourseRepository tripRecommendedCourseRepository;
    @Autowired private TripDailyScheduleRepository tripDailyScheduleRepository;
    @Autowired private TripDailySchedulePlaceRepository tripDailySchedulePlaceRepository;
    @Autowired private TripCourseTreatmentRepository tripCourseTreatmentRepository;

    @Test
    void deletingAccountRemovesUserAndAllLinkedData() throws Exception {
        User user = userRepository.save(
                new User("delete-me@example.com", "Delete Me", null, "google-sub-delete-me"));
        Long userId = user.getId();

        RecommendationResult result = recommendationResultRepository.save(
                new RecommendationResult(userId, "SUCCESS", "ok", Instant.now()));
        RecommendedCourse course = recommendedCourseRepository.save(new RecommendedCourse(
                result.getId(), 1, "course-1", new BigDecimal("4.50"), new BigDecimal("3.20")));
        coursePlaceRepository.save(
                new CoursePlace(course.getId(), 1, "테스트 카페", "카페", true, 1));

        tripSelectedCourseRepository.save(new TripSelectedCourse(userId, 999L, Instant.now()));
        TripRecommendedCourse tripCourse = tripRecommendedCourseRepository.save(
                new TripRecommendedCourse(userId, Instant.now(), 1, "course-2", new BigDecimal("1.10")));
        TripDailySchedule schedule = tripDailyScheduleRepository.save(new TripDailySchedule(
                tripCourse.getId(),
                LocalDate.now(),
                "시작점",
                new BigDecimal("127.00000000"),
                new BigDecimal("37.00000000")));
        tripDailySchedulePlaceRepository.save(new TripDailySchedulePlace(
                schedule.getId(),
                1,
                "테스트 관광지",
                "관광지",
                new BigDecimal("127.00000000"),
                new BigDecimal("37.00000000"),
                true,
                1,
                new BigDecimal("0.50")));
        tripCourseTreatmentRepository.save(
                new TripCourseTreatment(tripCourse.getId(), "리프팅", LocalDate.now()));

        String jwt = jwtProvider.generate(userId, user.getEmail(), user.getRole().name());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(recommendationResultRepository.findByUserIdOrderByRequestedAtDesc(userId)).isEmpty();
        assertThat(recommendedCourseRepository.findByRecommendationResultIdOrderByRank(result.getId()))
                .isEmpty();
        assertThat(coursePlaceRepository.findByRecommendedCourseIdOrderByVisitOrder(course.getId()))
                .isEmpty();
        assertThat(tripSelectedCourseRepository.findByUserIdOrderBySelectedAtDesc(userId)).isEmpty();
        assertThat(tripRecommendedCourseRepository.findByUserIdOrderByRequestedAtDesc(userId)).isEmpty();
        assertThat(tripDailyScheduleRepository.findByRecommendedCourseIdOrderByScheduleDate(tripCourse.getId()))
                .isEmpty();
        assertThat(tripDailySchedulePlaceRepository.findByDailyScheduleIdOrderByVisitOrder(schedule.getId()))
                .isEmpty();
        assertThat(tripCourseTreatmentRepository.findByRecommendedCourseId(tripCourse.getId())).isEmpty();
    }
}
