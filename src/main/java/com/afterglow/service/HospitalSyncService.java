package com.afterglow.service;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import com.afterglow.kakao.KakaoPlace;
import com.afterglow.kakao.KakaoPlaceClient;
import com.afterglow.kakao.SeoulDistricts;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.web.dto.MedicalTourismListItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관광공사 의료관광 목록과 카카오 로컬 API(HP8 병원 카테고리)를 조합해 {@link HospitalAccommodation}
 * 테이블의 HOSPITAL 행만 채운다(ACCOMMODATION 행은 이 서비스가 절대 건드리지 않는다). 범위는 서울 전체.
 *
 * <p>세 단계로 나뉜다.
 * <ol>
 *   <li>0단계: 서울 밖 주소를 가진 기존 HOSPITAL 행을 정리한다.</li>
 *   <li>1단계: 관광공사 목록을 순회하며 각 항목을 카카오에서 검색.
 *       매칭되면 source=TOURISM_API+KAKAO, 안 되면 관광공사 원본 데이터만으로
 *       source=TOURISM_API로 저장한다.</li>
 *   <li>2단계: 피부시술 관련 키워드(리프팅/보톡스 등)로 서울 25개 구를 훑어 피부시술병원 후보를
 *       찾고, {@code skinTreatmentConfidence}/{@code skinTreatmentSignals}를 채운다. 1단계에서
 *       이미 들어간 행이면(같은 kakaoPlaceId) 그 행에 태그만 얹고, 없으면 source=KAKAO로 새로 만든다.</li>
 * </ol>
 */
@Service
public class HospitalSyncService {

    private static final Logger log = LoggerFactory.getLogger(HospitalSyncService.class);

    private static final String SOURCE_BOTH = "TOURISM_API+KAKAO";
    private static final String SOURCE_TOURISM_ONLY = "TOURISM_API";
    private static final String SOURCE_KAKAO_ONLY = "KAKAO";

    // 카카오 검색은 한글 상호명 기준이라, 관광공사 기본 언어(ENG)가 아니라 한글로 명시 요청한다.
    private static final String KOREAN_LANG = "KOR";
    private static final String IN_SCOPE_ADDRESS_PREFIX = "서울";

    // 의료관광 API(langDivCd) 언어 코드. place_translations의 locale("ja"/"en")과는 표기가 다르다.
    private static final String JAPANESE_LANG = "JPN";
    private static final String ENGLISH_LANG = "ENG";

    /**
     * 피부시술 후보 탐지용 검색 키워드 10개. "{구이름} {키워드}" 형태로 카카오 키워드 검색에 그대로 넣는다.
     */
    private static final List<String> TREATMENT_KEYWORDS = List.of(
            "리프팅", "보톡스", "필러", "써마지", "울쎄라", "피부레이저", "여드름", "제모", "피부과", "피부클리닉");

    /** 브랜드 하위 세그먼트가 붙어도(예: "...피부과 > CNP차앤박피부과") startsWith로 허용된다. */
    private static final List<String> ALLOWED_HOSPITAL_CATEGORY_PREFIXES = List.of(
            "의료,건강 > 병원 > 피부과",
            "의료,건강 > 병원 > 성형외과",
            "의료,건강 > 병원 > 일반의원",
            "의료,건강 > 병원 > 가정의학과");

    private static final String DERMATOLOGY_CATEGORY_PREFIX = "의료,건강 > 병원 > 피부과";
    private static final String DERMATOLOGY_CATEGORY_SIGNAL = "kakao_category:피부과";

    private static final String CONFIDENCE_CONFIRMED = "confirmed";
    private static final String CONFIDENCE_HIGH = "high";
    private static final String CONFIDENCE_MEDIUM = "medium";
    private static final int HIGH_CONFIDENCE_SIGNAL_COUNT = 2;

    /** 화면에는 시술 특화 여부와 무관하게 전부 "병원"으로 통일 표기한다(수집 근거는 skinTreatment*에 남음). */
    private static final String HOSPITAL_DISPLAY_NAME = "병원";

    private final MedicalTourismService medicalTourismService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final HospitalAccommodationRepository hospitalAccommodationRepository;
    private final PlaceTranslationService placeTranslationService;

    public HospitalSyncService(
            MedicalTourismService medicalTourismService,
            KakaoPlaceClient kakaoPlaceClient,
            HospitalAccommodationRepository hospitalAccommodationRepository,
            PlaceTranslationService placeTranslationService) {
        this.medicalTourismService = medicalTourismService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.placeTranslationService = placeTranslationService;
    }

    @Transactional
    public SyncResult sync() {
        Instant syncedAt = Instant.now();

        List<MedicalTourismListItem> tourismItems = medicalTourismService.listAllSeoul(KOREAN_LANG);
        int pruned = pruneOutOfScope(tourismItems);
        Map<String, String> jaTitles = titleByContentId(JAPANESE_LANG);
        Map<String, String> enTitles = titleByContentId(ENGLISH_LANG);
        TourismPhaseResult tourismPhase = syncFromTourismApi(tourismItems, syncedAt, jaTitles, enTitles);
        SkinTreatmentPhaseResult skinTreatmentPhase = collectSkinTreatmentHospitals(syncedAt);

        log.info(
                "병원 동기화 완료: 범위밖삭제={}, 관광공사={}건 (양쪽매칭={}, 관광공사단독={}), "
                        + "피부시술 후보={}건 (신규={}, 기존행에 태그={}, confirmed={}/high={}/medium={})",
                pruned, tourismPhase.fetched, tourismPhase.matchedBoth, tourismPhase.tourismOnly,
                skinTreatmentPhase.candidatesFound, skinTreatmentPhase.newlyCreated, skinTreatmentPhase.merged,
                skinTreatmentPhase.confirmed, skinTreatmentPhase.high, skinTreatmentPhase.medium);

        return new SyncResult(
                pruned,
                tourismPhase.fetched,
                tourismPhase.matchedBoth,
                tourismPhase.tourismOnly,
                skinTreatmentPhase.candidatesFound,
                skinTreatmentPhase.newlyCreated,
                skinTreatmentPhase.merged,
                skinTreatmentPhase.confirmed,
                skinTreatmentPhase.high,
                skinTreatmentPhase.medium,
                syncedAt);
    }

    /**
     * 서울 밖 HOSPITAL 데이터를 삭제한다. ACCOMMODATION 행은 place_type으로 걸러내 절대 건드리지 않는다.
     * 카카오 출처가 있는 행(addressName이 항상 한글)은 주소 접두어로 판단하고, TOURISM_API 단독 행은
     * addressName이 langDivCd와 무관하게 로마자 표기라 텍스트로 판단할 수 없으므로, 이번에 새로 가져온
     * 서울 목록에 그 tourismContentId가 여전히 있는지로 판단한다.
     */
    private int pruneOutOfScope(List<MedicalTourismListItem> currentTourismItems) {
        Set<String> validTourismContentIds = new HashSet<>();
        for (MedicalTourismListItem item : currentTourismItems) {
            validTourismContentIds.add(item.contentId());
        }

        List<HospitalAccommodation> outOfScope = new ArrayList<>();
        for (HospitalAccommodation place : hospitalAccommodationRepository.findAll()) {
            if (place.getPlaceType() != PlaceType.HOSPITAL) {
                continue; // ACCOMMODATION은 이 동기화 대상이 아니다
            }
            if ("MANUAL".equals(place.getSource())) {
                continue; // 관리 페이지에서 직접 추가한 건 범위와 무관하게 유지
            }
            boolean inScope;
            if (SOURCE_TOURISM_ONLY.equals(place.getSource())) {
                inScope = validTourismContentIds.contains(place.getTourismContentId());
            } else {
                String address = place.getAddressName();
                inScope = address != null && address.startsWith(IN_SCOPE_ADDRESS_PREFIX);
            }
            if (!inScope) {
                outOfScope.add(place);
            }
        }
        if (!outOfScope.isEmpty()) {
            hospitalAccommodationRepository.deleteAll(outOfScope);
        }
        return outOfScope.size();
    }

    /**
     * place_translations 백필 전용 — 의료관광 목록 API는 langDivCd로 이미 언어별 title을 내려주므로,
     * (TourAPI와 달리) 건별 상세 조회 없이 목록을 언어별로 한 번 더 호출해 contentId→title 맵을 만든다.
     * 실패해도 동기화 본 작업을 막지 않도록 예외를 던지지 않는다.
     */
    private Map<String, String> titleByContentId(String medicalTourismLang) {
        Map<String, String> result = new HashMap<>();
        try {
            for (MedicalTourismListItem item : medicalTourismService.listAllSeoul(medicalTourismLang)) {
                if (StringUtils.hasText(item.contentId()) && StringUtils.hasText(item.title())) {
                    result.put(item.contentId(), item.title());
                }
            }
        } catch (Exception e) {
            log.warn("의료관광 API {} 번역 목록 조회 실패: {}", medicalTourismLang, e.getMessage());
        }
        return result;
    }

    /** contentId가 이번 목록에 있으면 의료관광 API 공식 번역을 적용한다(없으면 백필 스케줄러가 나중에 채움). */
    private void applyMedicalTourismTranslations(
            Long placeId, String contentId, Map<String, String> jaTitles, Map<String, String> enTitles) {
        if (!StringUtils.hasText(contentId)) {
            return;
        }
        placeTranslationService.applyPlaceName(
                PlaceType.HOSPITAL, placeId, "ja", jaTitles.get(contentId), "MEDICALTOURISM_JPN");
        placeTranslationService.applyPlaceName(
                PlaceType.HOSPITAL, placeId, "en", enTitles.get(contentId), "MEDICALTOURISM_ENG");
    }

    private TourismPhaseResult syncFromTourismApi(
            List<MedicalTourismListItem> items, Instant syncedAt, Map<String, String> jaTitles, Map<String, String> enTitles) {
        int matchedBoth = 0;
        int tourismOnly = 0;

        for (MedicalTourismListItem item : items) {
            String query = extractSearchQuery(item.title());
            Optional<KakaoPlace> match;
            try {
                match = kakaoPlaceClient.findHospital(query, item.mapX(), item.mapY());
            } catch (Exception e) {
                log.warn("카카오 검색 실패: title={}, error={}", item.title(), e.getMessage());
                match = Optional.empty();
            }

            String image = firstNonBlank(item.orgImage(), item.thumbImage());

            if (match.isPresent() && isHumanHospital(match.get())) {
                upsertMatched(item, match.get(), image, syncedAt, jaTitles, enTitles);
                matchedBoth++;
            } else {
                upsertTourismOnly(item, image, syncedAt, jaTitles, enTitles);
                tourismOnly++;
            }
        }
        return new TourismPhaseResult(items.size(), matchedBoth, tourismOnly);
    }

    private void upsertMatched(
            MedicalTourismListItem item,
            KakaoPlace kakaoPlace,
            String image,
            Instant syncedAt,
            Map<String, String> jaTitles,
            Map<String, String> enTitles) {
        HospitalAccommodation place = hospitalAccommodationRepository.findByPlaceId(kakaoPlace.id()).orElse(null);
        if (place == null) {
            place = hospitalAccommodationRepository.save(new HospitalAccommodation(
                    kakaoPlace.id(),
                    item.contentId(),
                    kakaoPlace.placeName(),
                    kakaoPlace.categoryGroupName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    PlaceType.HOSPITAL,
                    SOURCE_BOTH,
                    syncedAt));
        } else {
            place.updateFromSync(
                    kakaoPlace.placeName(),
                    kakaoPlace.addressName(),
                    kakaoPlace.mapX(),
                    kakaoPlace.mapY(),
                    image,
                    kakaoPlace.phone(),
                    kakaoPlace.placeUrl(),
                    SOURCE_BOTH,
                    syncedAt);
        }
        applyMedicalTourismTranslations(place.getId(), item.contentId(), jaTitles, enTitles);
    }

    private void upsertTourismOnly(
            MedicalTourismListItem item,
            String image,
            Instant syncedAt,
            Map<String, String> jaTitles,
            Map<String, String> enTitles) {
        BigDecimal mapX = parseOrNull(item.mapX());
        BigDecimal mapY = parseOrNull(item.mapY());
        if (mapX == null || mapY == null) {
            log.info("관광공사 좌표 없음, 스킵: title={}", item.title());
            return;
        }

        HospitalAccommodation place = hospitalAccommodationRepository.findByTourismContentId(item.contentId()).orElse(null);
        String address = firstNonBlank(item.baseAddr(), item.detailAddr());
        if (place == null) {
            place = hospitalAccommodationRepository.save(new HospitalAccommodation(
                    null,
                    item.contentId(),
                    item.title(),
                    null,
                    address,
                    mapX,
                    mapY,
                    image,
                    item.tel(),
                    null,
                    PlaceType.HOSPITAL,
                    SOURCE_TOURISM_ONLY,
                    syncedAt));
        } else {
            place.updateFromSync(
                    item.title(),
                    address,
                    mapX,
                    mapY,
                    image,
                    firstNonBlank(item.tel(), place.getPhone()),
                    place.getPlaceUrl(),
                    SOURCE_TOURISM_ONLY,
                    syncedAt);
        }
        applyMedicalTourismTranslations(place.getId(), item.contentId(), jaTitles, enTitles);
    }

    /**
     * "{구} {시술키워드}" 조합(25구 × 10키워드 = 250가지)으로 카카오 키워드 검색을 훑어 피부시술병원
     * 후보를 찾는다. 같은 병원이 여러 조합에서 발견되면 kakaoPlaceId로 병합하고 발견된 키워드를 전부
     * signals에 누적한다. 신뢰도는 다음 규칙으로 정한다:
     * <ul>
     *   <li>카테고리가 "...병원 > 피부과"(하위 브랜드 세그먼트 포함)로 시작 — signals 개수와 무관하게 confirmed</li>
     *   <li>피부과가 아니고 signals 2개 이상 — high</li>
     *   <li>피부과가 아니고 signals 1개 — medium</li>
     * </ul>
     * (키워드 검색으로만 후보를 발견하므로 signals가 0개인 경우는 애초에 생기지 않는다.)
     * 이미 1단계(관광공사 매칭)로 들어와 있는 행이면 새로 만들지 않고 그 행에 태그만 얹는다.
     */
    private SkinTreatmentPhaseResult collectSkinTreatmentHospitals(Instant syncedAt) {
        Map<String, SkinTreatmentCandidate> candidates = new LinkedHashMap<>();

        for (SeoulDistricts.Center district : SeoulDistricts.ALL) {
            for (String keyword : TREATMENT_KEYWORDS) {
                String query = district.name() + " " + keyword;
                List<KakaoPlace> found;
                try {
                    found = kakaoPlaceClient.searchHospitalKeywordAll(query);
                } catch (Exception e) {
                    log.warn("카카오 시술 키워드 검색 실패: query={}, error={}", query, e.getMessage());
                    continue;
                }
                for (KakaoPlace place : found) {
                    if (!isAllowedHospitalCategory(place.categoryName()) || !isInScope(place.addressName())) {
                        continue;
                    }
                    candidates.computeIfAbsent(place.id(), id -> SkinTreatmentCandidate.of(place))
                            .signals().add(keyword);
                }
            }
        }

        int newlyCreated = 0;
        int merged = 0;
        int confirmed = 0;
        int high = 0;
        int medium = 0;

        for (SkinTreatmentCandidate candidate : candidates.values()) {
            boolean isDermatology = isDermatologyCategory(candidate.place().categoryName());

            Set<String> signals = new LinkedHashSet<>();
            if (isDermatology) {
                signals.add(DERMATOLOGY_CATEGORY_SIGNAL);
            }
            signals.addAll(candidate.signals());

            String confidence;
            if (isDermatology) {
                confidence = CONFIDENCE_CONFIRMED;
                confirmed++;
            } else if (candidate.signals().size() >= HIGH_CONFIDENCE_SIGNAL_COUNT) {
                confidence = CONFIDENCE_HIGH;
                high++;
            } else {
                confidence = CONFIDENCE_MEDIUM;
                medium++;
            }

            HospitalAccommodation place = hospitalAccommodationRepository.findByPlaceId(candidate.place().id()).orElse(null);
            if (place == null) {
                place = hospitalAccommodationRepository.save(new HospitalAccommodation(
                        candidate.place().id(),
                        null,
                        candidate.place().placeName(),
                        candidate.place().categoryGroupName(),
                        candidate.place().addressName(),
                        candidate.place().mapX(),
                        candidate.place().mapY(),
                        null,
                        candidate.place().phone(),
                        candidate.place().placeUrl(),
                        PlaceType.HOSPITAL,
                        SOURCE_KAKAO_ONLY,
                        syncedAt));
                newlyCreated++;
            } else {
                merged++;
                // 오늘 카카오 검색으로 다시 확인된 데이터이므로 이름/주소/좌표/source를 최신으로 갱신한다.
                // CSV_IMPORT처럼 더 이상 살아있는 소스가 없던 행이 이렇게 자연스럽게 정리된다.
                // 관리자가 직접 등록/수정한 행(MANUAL)만 예외로 보호한다.
                if (!"MANUAL".equals(place.getSource())) {
                    place.updateFromSync(
                            candidate.place().placeName(),
                            candidate.place().addressName(),
                            candidate.place().mapX(),
                            candidate.place().mapY(),
                            null,
                            candidate.place().phone(),
                            candidate.place().placeUrl(),
                            SOURCE_KAKAO_ONLY,
                            syncedAt);
                }
            }
            place.applyMlTags(HOSPITAL_DISPLAY_NAME, confidence, String.join("|", signals));
        }

        return new SkinTreatmentPhaseResult(candidates.size(), newlyCreated, merged, confirmed, high, medium);
    }

    private static boolean isAllowedHospitalCategory(String categoryName) {
        if (categoryName == null) {
            return false;
        }
        for (String prefix : ALLOWED_HOSPITAL_CATEGORY_PREFIXES) {
            if (categoryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDermatologyCategory(String categoryName) {
        return categoryName != null && categoryName.startsWith(DERMATOLOGY_CATEGORY_PREFIX);
    }

    private static boolean isInScope(String addressName) {
        return addressName != null && addressName.startsWith(IN_SCOPE_ADDRESS_PREFIX);
    }

    /**
     * 카카오 HP8(병원) 카테고리에는 "가정,생활 > 반려동물 > 동물병원"도 섞여 나온다.
     * 사람 대상 의료기관("의료,건강 > ...")만 통과시킨다.
     */
    private static boolean isHumanHospital(KakaoPlace place) {
        String category = place.categoryName();
        return category != null && category.startsWith("의료,건강");
    }

    private static BigDecimal parseOrNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        if (StringUtils.hasText(b)) return b;
        return null;
    }

    /**
     * 관광공사 title은 "ATOP Plastic Surgery (에이탑 성형외과)"처럼 영문 상호명 뒤에
     * 괄호로 한글명이 붙는 경우가 많음. 카카오는 한글 상호명 기준 검색이 훨씬 잘 맞으므로
     * 마지막 괄호 안의 한글 부분을 추출해 검색어로 쓰고, 괄호가 없으면 원문 그대로 쓴다.
     */
    private static String extractSearchQuery(String title) {
        if (title == null) return null;
        int open = title.lastIndexOf('(');
        int close = title.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String inner = title.substring(open + 1, close).trim();
            if (!inner.isEmpty()) return inner;
        }
        return title;
    }

    private record SkinTreatmentCandidate(KakaoPlace place, Set<String> signals) {
        static SkinTreatmentCandidate of(KakaoPlace place) {
            return new SkinTreatmentCandidate(place, new LinkedHashSet<>());
        }
    }

    private record TourismPhaseResult(int fetched, int matchedBoth, int tourismOnly) {
    }

    private record SkinTreatmentPhaseResult(
            int candidatesFound, int newlyCreated, int merged, int confirmed, int high, int medium) {
    }

    public record SyncResult(
            int prunedOutOfScope,
            int tourismFetched,
            int matchedBoth,
            int tourismOnly,
            int skinTreatmentCandidates,
            int skinTreatmentNewlyCreated,
            int skinTreatmentMerged,
            int skinTreatmentConfirmed,
            int skinTreatmentHigh,
            int skinTreatmentMedium,
            Instant syncedAt) {
    }
}
