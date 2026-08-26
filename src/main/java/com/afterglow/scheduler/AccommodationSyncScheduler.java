package com.afterglow.scheduler;

import com.afterglow.config.AccommodationSyncProperties;
import com.afterglow.config.KakaoProperties;
import com.afterglow.config.TourApiProperties;
import com.afterglow.service.AccommodationSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccommodationSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccommodationSyncScheduler.class);

    private final AccommodationSyncProperties syncProperties;
    private final KakaoProperties kakaoProperties;
    private final TourApiProperties tourApiProperties;
    private final AccommodationSyncService accommodationSyncService;

    public AccommodationSyncScheduler(
            AccommodationSyncProperties syncProperties,
            KakaoProperties kakaoProperties,
            TourApiProperties tourApiProperties,
            AccommodationSyncService accommodationSyncService) {
        this.syncProperties = syncProperties;
        this.kakaoProperties = kakaoProperties;
        this.tourApiProperties = tourApiProperties;
        this.accommodationSyncService = accommodationSyncService;
    }

    @Scheduled(cron = "${accommodation.sync.cron}")
    public void syncAccommodations() {
        if (!syncProperties.enabled()) {
            return;
        }
        if (!kakaoProperties.isConfigured()) {
            log.warn("Skipping accommodation sync: kakao.rest-api-key(KAKAO_REST_API_KEY)가 설정되지 않았습니다.");
            return;
        }
        if (!tourApiProperties.isConfigured()) {
            log.warn("Skipping accommodation sync: tourapi.service-key(DATA_GO_KR_SERVICE_KEY)가 설정되지 않았습니다.");
            return;
        }
        try {
            accommodationSyncService.sync();
        } catch (Exception ex) {
            log.error("Scheduled accommodation sync failed", ex);
        }
    }
}
