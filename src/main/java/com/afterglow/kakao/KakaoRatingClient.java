package com.afterglow.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 카카오맵 장소 상세 페이지가 쓰는 비공식 내부 API로 평점/리뷰수를 가져와 0~5 인기도 점수를 계산한다.
 * 공식 REST API가 아니라서(서비스키 인증 없음, 브라우저 헤더 흉내) 언제든 응답 구조가 바뀌거나 막힐 수
 * 있다 — 실패 시 예외를 던지지 않고 보수적인 기본값(0점)으로 폴백한다.
 *
 * <p>공식/공개 문서가 없는 API이므로 실측으로 확인한 경로를 그대로 쓴다(2026-08-30):
 * <pre>
 * GET /places/panel3/{kakaoPlaceId}                        → kakaomap_review.score_set.{average_score,review_count}
 * GET /places/tab/reviews/blog/{kakaoPlaceId}?page=1        → review_count (블로그 리뷰 총개수, page=1이어도 전체 개수)
 * </pre>
 */
@Component
public class KakaoRatingClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoRatingClient.class);

    private static final double MAX_POPULARITY = 5.0;
    private static final double BLOG_SCORE_LOG_MULTIPLIER = 2.5;
    private static final double AVERAGE_SCORE_WEIGHT = 0.7;
    private static final double BLOG_SCORE_WEIGHT = 0.3;

    private final WebClient webClient;

    public KakaoRatingClient(@Qualifier("kakaoPlaceRatingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * popularity = round(averageScore*0.7 + blogScore*0.3) (평점 있음) 또는 round(blogScore) (평점 없음),
     * blogScore = min(5, log10(blogReviewCount+1)*2.5), 최종 0~5로 clamp.
     * 두 내부 API 중 하나라도 실패하면 0을 반환한다(호출부가 최소 인기도 기준으로 걸러내는 로직이라,
     * 실패를 관대하게 처리하면 오히려 검증 안 된 장소가 통과할 수 있어 보수적으로 낮게 잡는다).
     */
    public int calculatePopularity(String kakaoPlaceId) {
        Double averageScore = fetchAverageScore(kakaoPlaceId);
        int blogReviewCount = fetchBlogReviewCount(kakaoPlaceId);
        double blogScore = Math.min(MAX_POPULARITY, Math.log10(blogReviewCount + 1) * BLOG_SCORE_LOG_MULTIPLIER);

        double raw = averageScore != null
                ? averageScore * AVERAGE_SCORE_WEIGHT + blogScore * BLOG_SCORE_WEIGHT
                : blogScore;

        long rounded = Math.round(raw);
        return (int) Math.max(0, Math.min(MAX_POPULARITY, rounded));
    }

    /** score_set이 없으면(리뷰 0건인 곳이 실제로 있음, 실측 확인) null. */
    private Double fetchAverageScore(String kakaoPlaceId) {
        try {
            JsonNode root = webClient.get()
                    .uri("/places/panel3/{id}", kakaoPlaceId)
                    .header("Referer", "https://place.map.kakao.com/" + kakaoPlaceId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode scoreSet = root != null ? root.path("kakaomap_review").path("score_set") : null;
            if (scoreSet == null || scoreSet.isMissingNode() || !scoreSet.hasNonNull("average_score")) {
                return null;
            }
            return scoreSet.path("average_score").asDouble();
        } catch (Exception e) {
            log.warn("카카오맵 평점 조회 실패: kakaoPlaceId={}, error={}", kakaoPlaceId, e.getMessage());
            return null;
        }
    }

    private int fetchBlogReviewCount(String kakaoPlaceId) {
        try {
            JsonNode root = webClient.get()
                    .uri(builder -> builder.path("/places/tab/reviews/blog/{id}").queryParam("page", 1).build(kakaoPlaceId))
                    .header("Referer", "https://place.map.kakao.com/" + kakaoPlaceId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return root != null ? root.path("review_count").asInt(0) : 0;
        } catch (Exception e) {
            log.warn("카카오맵 블로그 리뷰수 조회 실패: kakaoPlaceId={}, error={}", kakaoPlaceId, e.getMessage());
            return 0;
        }
    }
}
