package com.afterglow.repository;

import com.afterglow.domain.RecommendedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendedCourseRepository extends JpaRepository<RecommendedCourse, Long> {

    List<RecommendedCourse> findByRecommendationResultIdOrderByRank(Long recommendationResultId);
}
