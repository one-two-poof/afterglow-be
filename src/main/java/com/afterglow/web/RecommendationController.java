package com.afterglow.web;

import com.afterglow.service.RecommendationService;
import com.afterglow.web.dto.RecommendationSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendation", description = "ML 추천 코스 결과 저장/조회. 전부 로그인(JWT) 필요")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(
            summary = "추천 코스 결과 저장",
            description = "ML 서버가 만든 추천 코스 응답을 로그인한 사용자 기준으로 저장한다. JWT 필요.")
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
}
