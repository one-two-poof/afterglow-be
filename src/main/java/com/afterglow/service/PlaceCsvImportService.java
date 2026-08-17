package com.afterglow.service;

import com.afterglow.domain.Place;
import com.afterglow.repository.PlaceRepository;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * data/raw/ml_data의 CSV(카카오 원본 필드 + ML 태그)를 kakaoPlaceId 기준으로 place 테이블에 반영한다.
 * 두 가지 모드가 있다.
 * <ul>
 *   <li>createIfMissing=false (예: 병원 ML 태그 백필) — 이미 카카오/관광공사 동기화로
 *       들어와 있는 행에 primaryType 등 ML 전용 필드만 채운다. 매칭 안 되면 그냥 건너뜀.</li>
 *   <li>createIfMissing=true (예: 숙소처럼 살아있는 동기화 소스가 없는 카테고리) —
 *       매칭 안 되면 CSV 값 그대로 새 행을 만든다(source=CSV_IMPORT).</li>
 * </ul>
 */
@Service
public class PlaceCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(PlaceCsvImportService.class);
    private static final CsvMapper CSV_MAPPER = new CsvMapper();

    private final PlaceRepository placeRepository;

    public PlaceCsvImportService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Transactional
    public ImportResult importFrom(Path csvPath, boolean createIfMissing, Place.PlaceType placeType) {
        File file = csvPath.toFile();
        if (!file.isFile()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CSV 파일을 찾을 수 없습니다: " + csvPath);
        }

        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        int matched = 0;
        int created = 0;
        int skipped = 0;

        try (MappingIterator<Map<String, String>> rows = CSV_MAPPER
                .readerForMapOf(String.class)
                .with(schema)
                .readValues(file)) {
            while (rows.hasNext()) {
                Map<String, String> row = rows.next();
                String kakaoPlaceId = row.get("kakaoPlaceId");
                if (!StringUtils.hasText(kakaoPlaceId)) {
                    skipped++;
                    continue;
                }

                Optional<Place> existing = placeRepository.findByPlaceId(kakaoPlaceId);
                if (existing.isPresent()) {
                    applyMlTagsFromRow(existing.get(), row);
                    matched++;
                    continue;
                }

                if (!createIfMissing) {
                    skipped++;
                    continue;
                }

                BigDecimal mapX = parseOrNull(row.get("mapX"));
                BigDecimal mapY = parseOrNull(row.get("mapY"));
                if (mapX == null || mapY == null) {
                    log.warn("좌표 없음, 스킵: placeName={}", row.get("placeName"));
                    skipped++;
                    continue;
                }

                Place place = Place.fromCsvRow(
                        kakaoPlaceId,
                        row.get("placeName"),
                        row.get("categoryName"),
                        row.get("categoryGroupCode"),
                        row.get("categoryGroupName"),
                        row.get("phone"),
                        row.get("addressName"),
                        row.get("roadAddressName"),
                        row.get("placeUrl"),
                        mapX,
                        mapY,
                        placeType,
                        Instant.now());
                applyMlTagsFromRow(place, row);
                placeRepository.save(place);
                created++;
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "CSV 파싱 실패: " + e.getMessage());
        }

        log.info("장소 CSV import 완료: matched={}, created={}, skipped={}", matched, created, skipped);
        return new ImportResult(matched, created, skipped);
    }

    /**
     * gangnam_seocho_places_with_constraints.csv 전용 컬럼(isIndoor 등)을 채운다.
     * 병원 CSV처럼 이 컬럼들이 없는 파일은 row.get()이 그냥 null을 반환해 전부 null로 들어간다.
     */
    private static void applyMlTagsFromRow(Place place, Map<String, String> row) {
        place.applyMlTags(
                row.get("primaryType"),
                row.get("primaryTypeName"),
                row.get("collectionTypes"),
                row.get("skinTreatmentConfidence"),
                row.get("skinTreatmentSignals"),
                parseBooleanOrNull(row.get("isIndoor")),
                parseBooleanOrNull(row.get("isHeatSource")),
                parseBooleanOrNull(row.get("isMassageSpot")),
                parseIntOrNull(row.get("walkHard")),
                parseBooleanOrNull(row.get("isNa")));
    }

    /** CSV 값은 "0"/"1"로 저장돼 있어 Boolean.parseBoolean으로는 못 읽는다. */
    private static Boolean parseBooleanOrNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return "1".equals(value.trim());
    }

    private static Integer parseIntOrNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseOrNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ImportResult(int matched, int created, int skipped) {
    }
}
