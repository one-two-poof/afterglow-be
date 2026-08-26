package com.afterglow.service;

import com.afterglow.tourapi.TourApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * TourAPI의 cat1/cat2/cat3는 목록 응답에 코드로만 내려오고 이름은 별도 엔드포인트
 * (categoryCode2)로 조회해야 한다. 대분류 7개/중분류 30~40개 수준으로 작아서, 조회한
 * 결과를 앱 실행 중 계속 캐싱해 API 호출을 최소화한다.
 */
@Service
public class TourApiCategoryService {

    private static final Logger log = LoggerFactory.getLogger(TourApiCategoryService.class);

    private final TourApiClient client;

    /** cat1 코드 -> 이름 */
    private final Map<String, String> cat1Names = new ConcurrentHashMap<>();
    /** "cat1|cat2" -> 이름 */
    private final Map<String, String> cat2Names = new ConcurrentHashMap<>();
    /** "cat1|cat2|cat3" -> 이름 */
    private final Map<String, String> cat3Names = new ConcurrentHashMap<>();

    /** 이미 전체를 긁어온 cat1(중분류 로딩 완료), "cat1|cat2"(소분류 로딩 완료) 집합 */
    private final Map<String, Boolean> cat2Loaded = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cat3Loaded = new ConcurrentHashMap<>();
    private volatile boolean cat1Loaded = false;

    public TourApiCategoryService(TourApiClient client) {
        this.client = client;
    }

    /** "인문(문화/예술/역사) > 문화시설 > 미술관/화랑" 형태로 조합. 조회 실패/코드 없음이면 null. */
    public String resolveHierarchy(String cat1, String cat2, String cat3) {
        String name1 = resolveCat1(cat1);
        String name2 = resolveCat2(cat1, cat2);
        String name3 = resolveCat3(cat1, cat2, cat3);

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, name1);
        appendIfPresent(sb, name2);
        appendIfPresent(sb, name3);
        return sb.length() == 0 ? null : sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" > ");
        }
        sb.append(value);
    }

    private String resolveCat1(String cat1) {
        if (!StringUtils.hasText(cat1)) {
            return null;
        }
        ensureCat1Loaded();
        return cat1Names.get(cat1);
    }

    private String resolveCat2(String cat1, String cat2) {
        if (!StringUtils.hasText(cat1) || !StringUtils.hasText(cat2)) {
            return null;
        }
        ensureCat2Loaded(cat1);
        return cat2Names.get(cat1 + "|" + cat2);
    }

    private String resolveCat3(String cat1, String cat2, String cat3) {
        if (!StringUtils.hasText(cat1) || !StringUtils.hasText(cat2) || !StringUtils.hasText(cat3)) {
            return null;
        }
        ensureCat3Loaded(cat1, cat2);
        return cat3Names.get(cat1 + "|" + cat2 + "|" + cat3);
    }

    private synchronized void ensureCat1Loaded() {
        if (cat1Loaded) {
            return;
        }
        try {
            for (JsonNode item : itemArray(client.fetchCategoryCode(null, null))) {
                cat1Names.put(item.path("code").asText(""), item.path("name").asText(""));
            }
            cat1Loaded = true;
        } catch (Exception e) {
            log.warn("TourAPI 대분류 코드 조회 실패: {}", e.getMessage());
        }
    }

    private synchronized void ensureCat2Loaded(String cat1) {
        if (cat2Loaded.containsKey(cat1)) {
            return;
        }
        try {
            for (JsonNode item : itemArray(client.fetchCategoryCode(cat1, null))) {
                cat2Names.put(cat1 + "|" + item.path("code").asText(""), item.path("name").asText(""));
            }
            cat2Loaded.put(cat1, true);
        } catch (Exception e) {
            log.warn("TourAPI 중분류 코드 조회 실패: cat1={}, error={}", cat1, e.getMessage());
        }
    }

    private synchronized void ensureCat3Loaded(String cat1, String cat2) {
        String key = cat1 + "|" + cat2;
        if (cat3Loaded.containsKey(key)) {
            return;
        }
        try {
            for (JsonNode item : itemArray(client.fetchCategoryCode(cat1, cat2))) {
                cat3Names.put(key + "|" + item.path("code").asText(""), item.path("name").asText(""));
            }
            cat3Loaded.put(key, true);
        } catch (Exception e) {
            log.warn("TourAPI 소분류 코드 조회 실패: cat1={}, cat2={}, error={}", cat1, cat2, e.getMessage());
        }
    }

    private java.util.List<JsonNode> itemArray(JsonNode root) {
        java.util.List<JsonNode> result = new java.util.ArrayList<>();
        JsonNode item = root.path("response").path("body").path("items").path("item");
        if (item.isArray()) {
            item.forEach(result::add);
        } else if (item.isObject()) {
            result.add(item);
        }
        return result;
    }
}
