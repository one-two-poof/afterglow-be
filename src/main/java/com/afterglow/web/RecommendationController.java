package com.afterglow.web;

import com.afterglow.service.RecommendationService;
import com.afterglow.web.dto.RecommendationResultResponse;
import com.afterglow.web.dto.RecommendationSubmitRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** 추천 코스 응답을 저장한다. */
    @PostMapping
    public ResponseEntity<Void> save(
            @AuthenticationPrincipal String userId,
            @RequestBody RecommendationSubmitRequest request) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        Long resultId = recommendationService.save(Long.parseLong(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/recommendations/" + resultId)
                .build();
    }

    /** 로그인한 사용자의 추천 이력 조회 (최신순) */
    @GetMapping
    public ResponseEntity<List<RecommendationResultResponse>> list(
            @AuthenticationPrincipal String userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(recommendationService.listByUser(Long.parseLong(userId)));
    }
}
