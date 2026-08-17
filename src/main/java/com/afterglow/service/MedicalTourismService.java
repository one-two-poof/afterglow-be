package com.afterglow.service;

import com.afterglow.config.MedicalTourismProperties;
import com.afterglow.medicaltourism.MedicalTourismClient;
import com.afterglow.medicaltourism.MedicalTourismClient.MedicalTourismApiException;
import com.afterglow.web.dto.MedicalTourismDetail;
import com.afterglow.web.dto.MedicalTourismListItem;
import com.afterglow.web.dto.MedicalTourismListResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MedicalTourismService {

    // mdclTursmSyncList는 지역 필터 파라미터를 지원하지 않으므로,
    // 전국 목록을 한 번에 받아온 뒤 법정동코드로 강남구/서초구만 걸러서 자체 페이징한다.
    // baseAddr는 langDivCd와 무관하게 로마자 표기라 "강남구" 문자열 매칭은 불가능함.
    private static final String SEOUL_REGN_CD = "11";
    private static final String GANGNAM_SIGNGU_CD = "680";
    private static final String SEOCHO_SIGNGU_CD = "650";
    private static final int FETCH_ALL_ROWS = 1000;

    private final MedicalTourismClient client;
    private final MedicalTourismProperties properties;

    public MedicalTourismService(MedicalTourismClient client, MedicalTourismProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public MedicalTourismListResponse getHospitals(int pageNo, int numOfRows, String lang) {
        List<MedicalTourismListItem> items = listAllGangnamSeocho(lang);

        int fromIndex = Math.min((pageNo - 1) * numOfRows, items.size());
        int toIndex = Math.min(fromIndex + numOfRows, items.size());

        return new MedicalTourismListResponse(
                pageNo,
                numOfRows,
                items.size(),
                items.subList(fromIndex, toIndex));
    }

    /** 강남구/서초구 의료관광 기관 전체 (페이징 없음) — 동기화 작업 등 내부 용도 */
    public List<MedicalTourismListItem> listAllGangnamSeocho(String lang) {
        JsonNode root = client.fetchList(1, FETCH_ALL_ROWS, resolveLang(lang));
        verifyHeader(root);

        JsonNode body = root.path("response").path("body");

        List<MedicalTourismListItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemArray(body)) {
            MedicalTourismListItem item = MedicalTourismListItem.from(itemNode);
            if (SEOUL_REGN_CD.equals(item.lDongRegnCd())
                    && (GANGNAM_SIGNGU_CD.equals(item.lDongSignguCd())
                            || SEOCHO_SIGNGU_CD.equals(item.lDongSignguCd()))) {
                items.add(item);
            }
        }
        return items;
    }

    public MedicalTourismDetail getHospitalDetail(String contentId, String lang) {
        JsonNode root = client.fetchDetail(contentId, resolveLang(lang));
        verifyHeader(root);

        JsonNode body = root.path("response").path("body");
        List<JsonNode> items = itemArray(body);
        if (items.isEmpty()) {
            throw new MedicalTourismApiException(
                    "contentId=" + contentId + " 에 대한 상세 정보를 찾을 수 없습니다.");
        }
        return MedicalTourismDetail.from(items.get(0));
    }

    private String resolveLang(String lang) {
        return StringUtils.hasText(lang) ? lang : properties.defaultLang();
    }

    private void verifyHeader(JsonNode root) {
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        // 0000 = 정상. 그 외에는 메시지와 함께 예외
        if (!code.isEmpty() && !"0000".equals(code)) {
            String msg = header.path("resultMsg").asText("UNKNOWN");
            throw new MedicalTourismApiException("공공데이터 API 오류 [" + code + "] " + msg);
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
