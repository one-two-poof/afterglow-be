package com.afterglow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TourApiConfig {

    // 공공데이터포털 응답이 큰 경우가 있어 버퍼를 넉넉히 잡는다.
    private static final int MAX_IN_MEMORY_BYTES = 8 * 1024 * 1024;

    @Bean
    WebClient tourApiWebClient(TourApiProperties properties) {
        return build(properties.baseUrl());
    }

    /** 일본어 번역(place_translations) 백필용 — JpnService2, KorService2와 endpoint/파라미터 형태는 동일. */
    @Bean
    WebClient tourApiJpnWebClient(TourApiProperties properties) {
        return build(properties.jpnBaseUrl());
    }

    /** 영어 번역(place_translations) 백필용 — EngService2, KorService2와 endpoint/파라미터 형태는 동일. */
    @Bean
    WebClient tourApiEngWebClient(TourApiProperties properties) {
        return build(properties.engBaseUrl());
    }

    private WebClient build(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
