package com.afterglow.repository;

import com.afterglow.domain.TripSelectedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSelectedCourseRepository extends JpaRepository<TripSelectedCourse, Long> {

    List<TripSelectedCourse> findByUserIdOrderBySelectedAtDesc(Long userId);
}
