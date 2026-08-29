package com.afterglow.scheduler;

import com.afterglow.config.PlaceTranslationBackfillProperties;
import com.afterglow.service.PlaceTranslationBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 병원/숙소/관광명소 동기화(04:00/04:30/04:45)가 TourAPI·의료관광 API 공식 번역으로 채우지 못한
 * 자리(카카오 단독 소스 등)를 {@link PlaceTranslationBackfillService}로 채운다.
 */
@Component
public class PlaceTranslationBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaceTranslationBackfillScheduler.class);

    private final PlaceTranslationBackfillProperties properties;
    private final PlaceTranslationBackfillService backfillService;

    public PlaceTranslationBackfillScheduler(
            PlaceTranslationBackfillProperties properties, PlaceTranslationBackfillService backfillService) {
        this.properties = properties;
        this.backfillService = backfillService;
    }

    @Scheduled(cron = "${place-translation.backfill.cron}")
    public void backfill() {
        if (!properties.enabled()) {
            return;
        }
        try {
            backfillService.backfill();
        } catch (Exception ex) {
            log.error("Scheduled place translation backfill failed", ex);
        }
    }
}
