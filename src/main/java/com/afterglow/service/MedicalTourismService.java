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

    private final MedicalTourismClient client;
    private final MedicalTourismProperties properties;

    public MedicalTourismService(MedicalTourismClient client, MedicalTourismProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public MedicalTourismListResponse getHospitals(int pageNo, int numOfRows, String lang) {
        JsonNode root = client.fetchList(pageNo, numOfRows, resolveLang(lang));
        verifyHeader(root);

        JsonNode body = root.path("response").path("body");

        List<MedicalTourismListItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemArray(body)) {
            items.add(MedicalTourismListItem.from(itemNode));
        }

        return new MedicalTourismListResponse(
                body.path("pageNo").asInt(pageNo),
                body.path("numOfRows").asInt(numOfRows),
                body.path("totalCount").asInt(items.size()),
                items);
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
