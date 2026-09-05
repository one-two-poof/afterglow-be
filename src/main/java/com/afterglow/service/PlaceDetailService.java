package com.afterglow.service;

import com.afterglow.domain.PlaceDetail;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.PlaceDetailRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * place_details 테이블에 대한 읽기/쓰기 primitive. 실제 상세 데이터 조회(TourAPI detailCommon2/
 * detailIntro2/detailImage2, 의료관광 detailMdclTursm)는 이 서비스를 호출하는 쪽
 * ({@link PlaceDetailBackfillService})이 알아서 하고, 여기서는 upsert/조회와 images/extraInfo
 * 직렬화만 담당한다.
 */
@Service
public class PlaceDetailService {

    private static final String IMAGE_DELIMITER = "|";

    private final PlaceDetailRepository repository;
    private final ObjectMapper objectMapper;

    public PlaceDetailService(PlaceDetailRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void applyDetail(
            PlaceType placeType, Long placeId, String overview, List<String> images,
            Map<String, String> extraInfo, String source) {
        if (!StringUtils.hasText(overview)) {
            return;
        }
        PlaceDetail detail = getOrCreate(placeType, placeId);
        detail.applyDetail(overview, joinImages(images), writeExtraInfo(extraInfo), source, Instant.now());
    }

    @Transactional
    public PlaceDetail getOrCreate(PlaceType placeType, Long placeId) {
        return repository.findByPlaceTypeAndPlaceId(placeType, placeId)
                .orElseGet(() -> repository.save(new PlaceDetail(placeType, placeId)));
    }

    /**
     * 관광명소 전용 — {@link AttractionSyncService}가 동기화 중 이미 알고 있는 TourAPI
     * contentTypeId(12/14/38)를 그대로 적어둔다(추가 API 호출 없음). 이 값이 있어야 백필 때
     * detailIntro2(운영정보)를 부를 수 있다. write-once라 이미 값이 있으면 아무것도 안 함.
     */
    @Transactional
    public void recordContentTypeId(PlaceType placeType, Long placeId, Integer contentTypeId) {
        if (contentTypeId == null) {
            return;
        }
        getOrCreate(placeType, placeId).applyContentTypeId(contentTypeId);
    }

    public Optional<PlaceDetail> find(PlaceType placeType, Long placeId) {
        return repository.findByPlaceTypeAndPlaceId(placeType, placeId);
    }

    public List<String> readImages(String images) {
        if (!StringUtils.hasText(images)) {
            return null;
        }
        return Arrays.asList(images.split("\\" + IMAGE_DELIMITER));
    }

    public Map<String, String> readExtraInfo(String extraInfo) {
        if (!StringUtils.hasText(extraInfo)) {
            return null;
        }
        try {
            return objectMapper.readValue(extraInfo, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return String.join(IMAGE_DELIMITER, images);
    }

    private String writeExtraInfo(Map<String, String> extraInfo) {
        if (extraInfo == null || extraInfo.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extraInfo);
        } catch (Exception e) {
            return null;
        }
    }
}
