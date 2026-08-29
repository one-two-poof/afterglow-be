package com.afterglow.service;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * data/raw/ml_data CSV(병원 1,427행/숙소 273행/관광지 501행)에서 실제 등장하는 이름·카테고리 중
 * 극히 일부를 손으로 번역해 넣은 구조 증명(PoC)용 provider다. 전체 백필에 쓰기엔 규모가 턱없이
 * 부족하다 — 실제 서비스에서는 이 클래스를 DeepL/Google Cloud Translation을 호출하는 구현체로
 * 교체하면 되고, 호출부({@link PlaceTranslationBackfillService})는 그대로 재사용된다.
 * 사전에 없는 한국어 텍스트는 {@code translate}가 null을 반환해 조용히 스킵된다(번역 누락을
 * 지어내지 않는다).
 */
@Component
public class SeedTranslationProvider implements TranslationProvider {

    private static final Map<String, Map<String, String>> PLACE_NAME_SEED = Map.ofEntries(
            Map.entry("10년전당신의오늘의원", Map.of("ja", "10年前のあなたの今日医院", "en", "10 Years Ago Clinic")),
            Map.entry("24게스트하우스 강남센터점", Map.of("ja", "24ゲストハウス江南センター店", "en", "24 Guesthouse Gangnam Center")),
            Map.entry("648호텔", Map.of("ja", "648ホテル", "en", "648 Hotel")),
            Map.entry("2448아트스페이스", Map.of("ja", "2448アートスペース", "en", "2448 Art Space")));

    private static final Map<String, Map<String, String>> CATEGORY_NAME_SEED = Map.ofEntries(
            Map.entry("의료,건강 > 병원 > 성형외과", Map.of(
                    "ja", "医療・健康 > 病院 > 整形外科",
                    "en", "Medical & Health > Hospital > Plastic Surgery")),
            Map.entry("여행 > 숙박 > 게스트하우스", Map.of(
                    "ja", "旅行 > 宿泊 > ゲストハウス",
                    "en", "Travel > Lodging > Guesthouse")),
            Map.entry("문화,예술 > 문화시설 > 미술관", Map.of(
                    "ja", "文化・芸術 > 文化施設 > 美術館",
                    "en", "Culture & Arts > Cultural Facility > Art Museum")));

    @Override
    public String translate(String koreanText, String targetLocale) {
        if (koreanText == null) {
            return null;
        }
        String key = koreanText.trim();
        String fromPlaceName = lookup(PLACE_NAME_SEED, key, targetLocale);
        if (fromPlaceName != null) {
            return fromPlaceName;
        }
        return lookup(CATEGORY_NAME_SEED, key, targetLocale);
    }

    private String lookup(Map<String, Map<String, String>> seed, String key, String locale) {
        Map<String, String> byLocale = seed.get(key);
        return byLocale == null ? null : byLocale.get(locale);
    }

    @Override
    public String sourceTag() {
        return "SEED_SAMPLE";
    }
}
