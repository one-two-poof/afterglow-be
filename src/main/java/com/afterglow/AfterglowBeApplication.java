package com.afterglow;

import com.afterglow.config.AccommodationSyncProperties;
import com.afterglow.config.AdminProperties;
import com.afterglow.config.AppCorsProperties;
import com.afterglow.config.AttractionSyncProperties;
import com.afterglow.config.HospitalSyncProperties;
import com.afterglow.config.JwtProperties;
import com.afterglow.config.KakaoProperties;
import com.afterglow.config.MedicalTourismProperties;
import com.afterglow.config.NotionProperties;
import com.afterglow.config.OAuthProperties;
import com.afterglow.config.PlaceDetailBackfillProperties;
import com.afterglow.config.PlaceTranslationBackfillProperties;
import com.afterglow.config.SyncProperties;
import com.afterglow.config.TourApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        NotionProperties.class,
        SyncProperties.class,
        AppCorsProperties.class,
        JwtProperties.class,
        MedicalTourismProperties.class,
        OAuthProperties.class,
        KakaoProperties.class,
        HospitalSyncProperties.class,
        AdminProperties.class,
        TourApiProperties.class,
        AccommodationSyncProperties.class,
        AttractionSyncProperties.class,
        PlaceTranslationBackfillProperties.class,
        PlaceDetailBackfillProperties.class
})
public class AfterglowBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterglowBeApplication.class, args);
    }
}
