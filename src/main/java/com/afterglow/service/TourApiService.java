package com.afterglow.service;

import com.afterglow.kakao.SeoulDistricts;
import com.afterglow.tourapi.TourApiClient;
import com.afterglow.tourapi.TourApiClient.TourApiException;
import com.afterglow.tourapi.TourApiLanguageClient;
import com.afterglow.web.dto.TourApiListItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TourApiService {

    private static final Logger log = LoggerFactory.getLogger(TourApiService.class);

    private static final String SEOUL_AREA_CODE = "1";
    private static final List<String> SEOUL_SIGUNGU_CODES = SeoulDistricts.ALL.stream()
            .map(SeoulDistricts.Center::tourApiSigunguCode)
            .toList();
    private static final int FETCH_ALL_ROWS = 500;

    private final TourApiClient client;
    private final TourApiLanguageClient languageClient;

    public TourApiService(TourApiClient client, TourApiLanguageClient languageClient) {
        this.client = client;
        this.languageClient = languageClient;
    }

    /** 서울 25개 구의 특정 contentTypeId(예: 32=숙박) 목록 전체 (페이징 없음) — 동기화 작업 전용 */
    public List<TourApiListItem> listSeoulByContentType(int contentTypeId) {
        List<TourApiListItem> items = new ArrayList<>();
        for (String sigunguCode : SEOUL_SIGUNGU_CODES) {
            JsonNode root = client.fetchAreaBasedList(contentTypeId, SEOUL_AREA_CODE, sigunguCode, 1, FETCH_ALL_ROWS);
            verifyHeader(root);
            JsonNode body = root.path("response").path("body");
            for (JsonNode itemNode : itemArray(body)) {
                items.add(TourApiListItem.from(itemNode));
            }
        }
        return items;
    }

    /**
     * place_translations 백필 전용 — 서울 25개 구의 특정 contentTypeId 목록을 지정 언어(locale: "ja"/"en")로
     * 조회해 contentId → title 맵으로 반환한다. 실패해도 동기화 본 작업(한글 목록 처리)을 막지 않도록
     * 예외를 던지지 않고 빈 맵을 반환한다.
     */
    public Map<String, String> titleByContentId(int contentTypeId, String locale) {
        Map<String, String> result = new HashMap<>();
        try {
            for (String sigunguCode : SEOUL_SIGUNGU_CODES) {
                JsonNode root = languageClient.fetchAreaBasedList(
                        locale, contentTypeId, SEOUL_AREA_CODE, sigunguCode, 1, FETCH_ALL_ROWS);
                if (root == null) {
                    continue;
                }
                verifyHeader(root);
                for (JsonNode itemNode : itemArray(root.path("response").path("body"))) {
                    TourApiListItem item = TourApiListItem.from(itemNode);
                    if (!item.contentId().isBlank() && !item.title().isBlank()) {
                        result.put(item.contentId(), item.title());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("TourAPI {} 번역 목록 조회 실패: contentTypeId={}, error={}", locale, contentTypeId, e.getMessage());
        }
        return result;
    }

    private void verifyHeader(JsonNode root) {
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        if (!code.isEmpty() && !"0000".equals(code)) {
            String msg = header.path("resultMsg").asText("UNKNOWN");
            throw new TourApiException("공공데이터 API 오류 [" + code + "] " + msg);
        }
    }

    /**
     * response.body.items.item 을 List로 변환.
     * item은 결과가 1건이면 객체, 여러 건이면 배열, 없으면 누락/빈문자열일 수 있어 모두 처리한다.
     */
    private List<JsonNode> itemArray(JsonNode body) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode item = body.path("items").path("item");
        if (item.isArray()) {
            item.forEach(result::add);
        } else if (item.isObject()) {
            result.add(item);
        }
        return result;
    }
}
