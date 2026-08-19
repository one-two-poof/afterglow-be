package com.afterglow.repository;

import com.afterglow.domain.TripDailyCoursePlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailyCoursePlaceRepository extends JpaRepository<TripDailyCoursePlace, Long> {

    List<TripDailyCoursePlace> findByDailyCourseIdOrderByVisitOrder(Long dailyCourseId);
}
