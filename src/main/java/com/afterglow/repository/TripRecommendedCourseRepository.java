package com.afterglow.repository;

import com.afterglow.domain.TripRecommendedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRecommendedCourseRepository extends JpaRepository<TripRecommendedCourse, Long> {

    List<TripRecommendedCourse> findByUserIdOrderByRequestedAtDesc(Long userId);
}
