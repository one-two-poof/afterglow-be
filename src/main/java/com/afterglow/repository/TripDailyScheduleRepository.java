package com.afterglow.repository;

import com.afterglow.domain.TripDailySchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailyScheduleRepository extends JpaRepository<TripDailySchedule, Long> {

    List<TripDailySchedule> findByRecommendedCourseIdOrderByScheduleDate(Long recommendedCourseId);
}
