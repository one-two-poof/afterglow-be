package com.afterglow.service;

import com.afterglow.tourapi.TourApiClient;
import com.afterglow.tourapi.TourApiClient.TourApiException;
import com.afterglow.web.dto.TourApiListItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TourApiService {

    private static final String SEOUL_AREA_CODE = "1";
    // areaCode2(areaCode=1) 조회로 확인한 서울 구 코드 — 강남구=1, 서초구=15
    private static final List<String> GANGNAM_SEOCHO_SIGUNGU_CODES = List.of("1", "15");
    private static final int FETCH_ALL_ROWS = 500;

    private final TourApiClient client;

    public TourApiService(TourApiClient client) {
        this.client = client;
    }

    /** 강남구/서초구의 특정 contentTypeId(예: 32=숙박) 목록 전체 (페이징 없음) — 동기화 작업 전용 */
    public List<TourApiListItem> listGangnamSeochoByContentType(int contentTypeId) {
        List<TourApiListItem> items = new ArrayList<>();
        for (String sigunguCode : GANGNAM_SEOCHO_SIGUNGU_CODES) {
            JsonNode root = client.fetchAreaBasedList(contentTypeId, SEOUL_AREA_CODE, sigunguCode, 1, FETCH_ALL_ROWS);
            verifyHeader(root);
            JsonNode body = root.path("response").path("body");
            for (JsonNode itemNode : itemArray(body)) {
                items.add(TourApiListItem.from(itemNode));
            }
        }
        return items;
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
