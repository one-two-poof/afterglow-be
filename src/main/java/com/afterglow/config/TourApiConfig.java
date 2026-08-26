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
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
