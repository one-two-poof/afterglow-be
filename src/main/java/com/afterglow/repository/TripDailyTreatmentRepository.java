package com.afterglow.repository;

import com.afterglow.domain.TripDailyTreatment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailyTreatmentRepository extends JpaRepository<TripDailyTreatment, Long> {

    List<TripDailyTreatment> findByDailyRecommendationId(Long dailyRecommendationId);
}
