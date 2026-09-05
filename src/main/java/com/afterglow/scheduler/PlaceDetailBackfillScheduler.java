package com.afterglow.scheduler;

import com.afterglow.config.PlaceDetailBackfillProperties;
import com.afterglow.service.PlaceDetailBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 병원/숙소/관광명소 동기화(04:00/04:30/04:45)가 끝난 뒤, tourism_content_id가 있는 행의
 * place_details(소개글/이미지/운영정보)를 {@link PlaceDetailBackfillService}로 채운다.
 * 번역 백필(05:30)보다 먼저 돌게 배치했다(서로 독립적인 작업이라 순서 자체는 중요하지 않지만,
 * place-translations.md의 백필 스케줄 나열 순서와 맞춤).
 */
@Component
public class PlaceDetailBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailBackfillScheduler.class);

    private final PlaceDetailBackfillProperties properties;
    private final PlaceDetailBackfillService backfillService;

    public PlaceDetailBackfillScheduler(
            PlaceDetailBackfillProperties properties, PlaceDetailBackfillService backfillService) {
        this.properties = properties;
        this.backfillService = backfillService;
    }

    @Scheduled(cron = "${place-detail.backfill.cron}")
    public void backfill() {
        if (!properties.enabled()) {
            return;
        }
        try {
            backfillService.backfill();
        } catch (Exception ex) {
            log.error("Scheduled place detail backfill failed", ex);
        }
    }
}
