package com.afterglow.service;

import com.afterglow.tourapi.TourApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * TourAPI KorService2 상세 조회(detailCommon2/detailIntro2/detailImage2) 결과를
 * {@link PlaceDetailService}가 저장할 수 있는 형태(overview/images/extraInfo)로 변환한다.
 * 숙소(contentTypeId=32 고정)에만 detailIntro2를 부른다 — 관광명소는 contentTypeId를 몰라서
 * overview/images만 받는다(docs/place-detail-info-plan.md 0절 참고).
 */
@Service
public class TourApiDetailService {

    private static final int ACCOMMODATION_CONTENT_TYPE_ID = 32;

    private final TourApiClient client;

    public TourApiDetailService(TourApiClient client) {
        this.client = client;
    }

    /** 관광명소 전용 — overview/images만, extraInfo는 항상 null. */
    public Detail fetchAttractionDetail(String contentId) {
        String overview = fetchOverview(contentId);
        if (!StringUtils.hasText(overview)) {
            return null;
        }
        return new Detail(overview, fetchImages(contentId), null);
    }

    /** 숙소 전용 — overview/images에 더해 체크인/체크아웃 등 운영정보(extraInfo)까지 받는다. */
    public Detail fetchAccommodationDetail(String contentId) {
        String overview = fetchOverview(contentId);
        if (!StringUtils.hasText(overview)) {
            return null;
        }
        return new Detail(overview, fetchImages(contentId), fetchAccommodationIntro(contentId));
    }

    private String fetchOverview(String contentId) {
        JsonNode root = client.fetchDetailCommon2(contentId);
        List<JsonNode> items = itemArray(root.path("response").path("body"));
        if (items.isEmpty()) {
            return null;
        }
        return items.get(0).path("overview").asText("");
    }

    private List<String> fetchImages(String contentId) {
        JsonNode root = client.fetchDetailImage2(contentId);
        List<JsonNode> items = itemArray(root.path("response").path("body"));
        List<String> images = new ArrayList<>();
        for (JsonNode item : items) {
            String url = item.path("originimgurl").asText("");
            if (StringUtils.hasText(url)) {
                images.add(url);
            }
        }
        return images.isEmpty() ? null : images;
    }

    private Map<String, String> fetchAccommodationIntro(String contentId) {
        JsonNode root = client.fetchDetailIntro2(contentId, ACCOMMODATION_CONTENT_TYPE_ID);
        List<JsonNode> items = itemArray(root.path("response").path("body"));
        if (items.isEmpty()) {
            return null;
        }
        JsonNode item = items.get(0);
        Map<String, String> extraInfo = new LinkedHashMap<>();
        putIfPresent(extraInfo, "checkinTime", item, "checkintime");
        putIfPresent(extraInfo, "checkoutTime", item, "checkouttime");
        putIfPresent(extraInfo, "roomCount", item, "roomcount");
        putIfPresent(extraInfo, "subFacility", item, "subfacility");
        putIfPresent(extraInfo, "parking", item, "parkinglodging");
        putIfPresent(extraInfo, "cooking", item, "chkcooking");
        putIfPresent(extraInfo, "pickup", item, "pickup");
        putIfPresent(extraInfo, "reservationUrl", item, "reservationurl");
        putIfPresent(extraInfo, "scale", item, "scale");
        return extraInfo.isEmpty() ? null : extraInfo;
    }

    private void putIfPresent(Map<String, String> map, String key, JsonNode item, String sourceField) {
        String value = item.path(sourceField).asText("");
        if (StringUtils.hasText(value)) {
            map.put(key, value);
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

    public record Detail(String overview, List<String> images, Map<String, String> extraInfo) {
    }
}
