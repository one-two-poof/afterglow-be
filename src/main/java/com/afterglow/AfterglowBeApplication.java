package com.afterglow;

import com.afterglow.config.AppCorsProperties;
import com.afterglow.config.HospitalSyncProperties;
import com.afterglow.config.JwtProperties;
import com.afterglow.config.KakaoProperties;
import com.afterglow.config.MedicalTourismProperties;
import com.afterglow.config.NotionProperties;
import com.afterglow.config.OAuthProperties;
import com.afterglow.config.SyncProperties;
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
        HospitalSyncProperties.class
})
public class AfterglowBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterglowBeApplication.class, args);
    }
}
