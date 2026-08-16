package com.afterglow.repository;

import com.afterglow.domain.RecommendationResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {

    List<RecommendationResult> findByUserIdOrderByRequestedAtDesc(Long userId);
}
