package com.afterglow.repository;

import com.afterglow.domain.TripDailyRecommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripDailyRecommendationRepository extends JpaRepository<TripDailyRecommendation, Long> {

    List<TripDailyRecommendation> findByUserIdOrderByRecDate(Long userId);
}
