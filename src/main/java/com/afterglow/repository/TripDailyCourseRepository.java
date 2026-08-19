package com.afterglow.repository;

import com.afterglow.domain.TripDailyCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailyCourseRepository extends JpaRepository<TripDailyCourse, Long> {

    List<TripDailyCourse> findByDailyRecommendationIdOrderByRank(Long dailyRecommendationId);
}
