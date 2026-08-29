package com.afterglow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KakaoConfig {

    @Bean
    WebClient kakaoWebClient(KakaoProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "KakaoAK " + properties.restApiKey())
                .build();
    }

    /**
     * 카카오맵 장소 상세 페이지가 내부적으로 쓰는 비공식 API(place-api.map.kakao.com) — 공식 REST API
     * 키/Authorization과 무관하고, 브라우저처럼 보이는 헤더로 요청한다. popularity 계산 전용
     * ({@link com.afterglow.kakao.KakaoRatingClient}).
     */
    @Bean
    WebClient kakaoPlaceRatingWebClient() {
        return WebClient.builder()
                .baseUrl("https://place-api.map.kakao.com")
                .defaultHeader("pf", "PC")
                .defaultHeader("appVersion", "6.6.0")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
