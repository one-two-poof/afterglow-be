package com.afterglow.scheduler;

import com.afterglow.config.AttractionSyncProperties;
import com.afterglow.config.KakaoProperties;
import com.afterglow.config.TourApiProperties;
import com.afterglow.service.AttractionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttractionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttractionSyncScheduler.class);

    private final AttractionSyncProperties syncProperties;
    private final KakaoProperties kakaoProperties;
    private final TourApiProperties tourApiProperties;
    private final AttractionSyncService attractionSyncService;

    public AttractionSyncScheduler(
            AttractionSyncProperties syncProperties,
            KakaoProperties kakaoProperties,
            TourApiProperties tourApiProperties,
            AttractionSyncService attractionSyncService) {
        this.syncProperties = syncProperties;
        this.kakaoProperties = kakaoProperties;
        this.tourApiProperties = tourApiProperties;
        this.attractionSyncService = attractionSyncService;
    }

    @Scheduled(cron = "${attraction.sync.cron}")
    public void syncAttractions() {
        if (!syncProperties.enabled()) {
            return;
        }
        if (!kakaoProperties.isConfigured()) {
            log.warn("Skipping attraction sync: kakao.rest-api-key(KAKAO_REST_API_KEY)가 설정되지 않았습니다.");
            return;
        }
        if (!tourApiProperties.isConfigured()) {
            log.warn("Skipping attraction sync: tourapi.service-key(DATA_GO_KR_SERVICE_KEY)가 설정되지 않았습니다.");
            return;
        }
        try {
            attractionSyncService.sync();
        } catch (Exception ex) {
            log.error("Scheduled attraction sync failed", ex);
        }
    }
}
