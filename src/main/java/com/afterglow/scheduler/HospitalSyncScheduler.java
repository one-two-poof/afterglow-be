package com.afterglow.scheduler;

import com.afterglow.config.HospitalSyncProperties;
import com.afterglow.config.KakaoProperties;
import com.afterglow.service.HospitalSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HospitalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(HospitalSyncScheduler.class);

    private final HospitalSyncProperties syncProperties;
    private final KakaoProperties kakaoProperties;
    private final HospitalSyncService hospitalSyncService;

    public HospitalSyncScheduler(
            HospitalSyncProperties syncProperties,
            KakaoProperties kakaoProperties,
            HospitalSyncService hospitalSyncService) {
        this.syncProperties = syncProperties;
        this.kakaoProperties = kakaoProperties;
        this.hospitalSyncService = hospitalSyncService;
    }

    @Scheduled(cron = "${hospital.sync.cron}")
    public void syncHospitals() {
        if (!syncProperties.enabled()) {
            return;
        }
        if (!kakaoProperties.isConfigured()) {
            log.warn("Skipping hospital sync: kakao.rest-api-key(KAKAO_REST_API_KEY)가 설정되지 않았습니다.");
            return;
        }
        try {
            hospitalSyncService.sync();
        } catch (Exception ex) {
            log.error("Scheduled hospital sync failed", ex);
        }
    }
}
