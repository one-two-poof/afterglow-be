package com.afterglow.service;

import com.afterglow.domain.PlaceTranslation;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.PlaceTranslationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * place_translations 테이블에 대한 읽기/쓰기 primitive. 실제 번역 소스(TourAPI 언어별 서비스,
 * 의료관광 API langDivCd, {@link TranslationProvider})는 이 서비스를 호출하는 쪽
 * ({@link AttractionSyncService}, {@link AccommodationSyncService}, {@link HospitalSyncService},
 * {@link PlaceTranslationBackfillService})이 각자 알아서 조회하고, 여기서는 upsert/조회만 담당한다.
 */
@Service
public class PlaceTranslationService {

    /** 지금 지원하는 로케일. ko(원본)는 이 테이블에 row를 두지 않고 원본 테이블 값을 그대로 쓴다. */
    public static final Set<String> SUPPORTED_LOCALES = Set.of("ja", "en");

    private final PlaceTranslationRepository repository;

    public PlaceTranslationService(PlaceTranslationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void applyPlaceName(PlaceType placeType, Long placeId, String locale, String placeName, String source) {
        if (placeName == null || placeName.isBlank()) {
            return;
        }
        getOrCreate(placeType, placeId, locale).applyPlaceName(placeName, source, Instant.now());
    }

    @Transactional
    public void applyCategoryName(PlaceType placeType, Long placeId, String locale, String categoryName, String source) {
        if (categoryName == null || categoryName.isBlank()) {
            return;
        }
        getOrCreate(placeType, placeId, locale).applyCategoryName(categoryName, source, Instant.now());
    }

    @Transactional
    public PlaceTranslation getOrCreate(PlaceType placeType, Long placeId, String locale) {
        return repository.findByPlaceTypeAndPlaceIdAndLocale(placeType, placeId, locale)
                .orElseGet(() -> repository.save(new PlaceTranslation(placeType, placeId, locale)));
    }

    /**
     * 목록 조회 API 응답에 번역을 얹을 때 쓰는 배치 조회 — 결과 건수와 무관하게 쿼리 1번만 나간다
     * (건별로 조회하면 N+1이 된다). locale이 지원 로케일이 아니면 빈 맵.
     */
    public Map<String, PlaceTranslation> findByPlacesAndLocale(
            Collection<PlaceType> placeTypes, Collection<Long> placeIds, String locale) {
        if (locale == null || placeIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByPlaceTypeInAndPlaceIdInAndLocale(placeTypes, placeIds, locale).stream()
                .collect(Collectors.toMap(t -> key(t.getPlaceType(), t.getPlaceId()), t -> t, (a, b) -> a));
    }

    public static String key(PlaceType placeType, Long placeId) {
        return placeType + ":" + placeId;
    }

    /** 'ko'나 지원하지 않는 값이면 null(= 원본 그대로 응답). */
    public static String normalizeLocale(String lang) {
        if (lang == null) {
            return null;
        }
        String normalized = lang.trim().toLowerCase();
        return SUPPORTED_LOCALES.contains(normalized) ? normalized : null;
    }
}
