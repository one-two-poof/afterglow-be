package com.afterglow.repository;

import com.afterglow.domain.TripCourseTreatment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripCourseTreatmentRepository extends JpaRepository<TripCourseTreatment, Long> {

    List<TripCourseTreatment> findByRecommendedCourseId(Long recommendedCourseId);
}
