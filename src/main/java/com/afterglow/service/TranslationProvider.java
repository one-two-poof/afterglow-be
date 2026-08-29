package com.afterglow.service;

/**
 * TourAPI/의료관광 API처럼 공식 번역이 없는 행(CSV·카카오 단독 소스)의 텍스트를 번역한다.
 * 지금은 {@link SeedTranslationProvider}(소규모 샘플 하드코딩)로 구조만 증명하고, 실제 서비스 규모
 * 백필이 필요해지면 DeepL/Google Cloud Translation 등을 호출하는 구현체로 교체하면 된다 — 호출부
 * ({@link PlaceTranslationBackfillService})는 이 인터페이스에만 의존하므로 교체해도 영향이 없다.
 */
public interface TranslationProvider {

    /** koreanText를 targetLocale("ja"|"en")로 번역. 번역 불가/실패면 null(호출부는 그 필드를 비워둔 채 스킵). */
    String translate(String koreanText, String targetLocale);

    /** PlaceTranslation.source에 기록할 값. */
    String sourceTag();
}
