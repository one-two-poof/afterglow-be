package com.afterglow.service;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceTranslation;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.repository.HospitalAccommodationRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * TourAPI/의료관광 API로 공식 번역을 못 받은 행(CSV·카카오 단독 소스, 또는 TourAPI 목록에 그
 * contentId가 이번엔 없었던 행)의 place_name/category_name 빈 자리를 {@link TranslationProvider}로
 * 채운다. attractions/hospitals_accommodations 전체를 훑는 배치 작업이라 스케줄러에서만 호출한다
 * (요청 경로에서 호출하지 않음).
 */
@Service
public class PlaceTranslationBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PlaceTranslationBackfillService.class);
    private static final List<String> LOCALES = List.of("ja", "en");

    private final AttractionRepository attractionRepository;
    private final HospitalAccommodationRepository hospitalAccommodationRepository;
    private final PlaceTranslationService placeTranslationService;
    private final TranslationProvider translationProvider;

    public PlaceTranslationBackfillService(
            AttractionRepository attractionRepository,
            HospitalAccommodationRepository hospitalAccommodationRepository,
            PlaceTranslationService placeTranslationService,
            TranslationProvider translationProvider) {
        this.attractionRepository = attractionRepository;
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.placeTranslationService = placeTranslationService;
        this.translationProvider = translationProvider;
    }

    @Transactional
    public BackfillResult backfill() {
        int placeNameFilled = 0;
        int categoryNameFilled = 0;

        for (Attraction attraction : attractionRepository.findAll()) {
            Counts counts = backfillOne(
                    PlaceType.ATTRACTION, attraction.getId(), attraction.getPlaceName(), attraction.getCategoryName());
            placeNameFilled += counts.placeName();
            categoryNameFilled += counts.categoryName();
        }
        for (HospitalAccommodation place : hospitalAccommodationRepository.findAll()) {
            Counts counts = backfillOne(
                    place.getPlaceType(), place.getId(), place.getPlaceName(), place.getCategoryName());
            placeNameFilled += counts.placeName();
            categoryNameFilled += counts.categoryName();
        }

        log.info(
                "번역 백필 완료: placeName={}건, categoryName={}건 채움 (provider={})",
                placeNameFilled, categoryNameFilled, translationProvider.sourceTag());
        return new BackfillResult(placeNameFilled, categoryNameFilled);
    }

    private Counts backfillOne(PlaceType placeType, Long placeId, String koreanName, String koreanCategory) {
        int nameFilled = 0;
        int categoryFilled = 0;

        for (String locale : LOCALES) {
            PlaceTranslation row = placeTranslationService.getOrCreate(placeType, placeId, locale);

            if (!row.isPlaceNameFilled()) {
                String translated = translationProvider.translate(koreanName, locale);
                if (translated != null) {
                    row.applyPlaceName(translated, translationProvider.sourceTag(), Instant.now());
                    nameFilled++;
                }
            }
            if (!row.isCategoryNameFilled() && StringUtils.hasText(koreanCategory)) {
                String translated = translationProvider.translate(koreanCategory, locale);
                if (translated != null) {
                    row.applyCategoryName(translated, translationProvider.sourceTag(), Instant.now());
                    categoryFilled++;
                }
            }
        }
        return new Counts(nameFilled, categoryFilled);
    }

    private record Counts(int placeName, int categoryName) {
    }

    public record BackfillResult(int placeNameFilled, int categoryNameFilled) {
    }
}
