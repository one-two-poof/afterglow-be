package com.afterglow.repository;

import com.afterglow.domain.CoursePlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoursePlaceRepository extends JpaRepository<CoursePlace, Long> {

    List<CoursePlace> findByRecommendedCourseIdOrderByVisitOrder(Long recommendedCourseId);
}
