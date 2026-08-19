package com.afterglow.repository;

import com.afterglow.domain.TripDailySchedulePlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailySchedulePlaceRepository extends JpaRepository<TripDailySchedulePlace, Long> {

    List<TripDailySchedulePlace> findByDailyScheduleIdOrderByVisitOrder(Long dailyScheduleId);
}
