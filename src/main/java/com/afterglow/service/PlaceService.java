package com.afterglow.service;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceTranslation;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 장소는 병원/숙소({@link HospitalAccommodation})와 관광명소({@link Attraction}) 두 테이블로 나뉘어 있다.
 * id는 테이블마다 독립적으로 채번되므로(1번 행이 두 테이블에 동시에 존재할 수 있음), 단건 조회/수정/삭제는
 * 반드시 placeType을 같이 받아 어느 테이블을 볼지 정한다.
 */
@Service
public class PlaceService {

    private final HospitalAccommodationRepository hospitalAccommodationRepository;
    private final AttractionRepository attractionRepository;
    private final PlaceTranslationService placeTranslationService;

    public PlaceService(
            HospitalAccommodationRepository hospitalAccommodationRepository,
            AttractionRepository attractionRepository,
            PlaceTranslationService placeTranslationService) {
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.attractionRepository = attractionRepository;
        this.placeTranslationService = placeTranslationService;
    }

    public List<PlaceResponse> listAll(
            String name, String lang, BigDecimal swLat, BigDecimal neLat, BigDecimal swLng, BigDecimal neLng) {
        validateBounds(swLat, neLat, swLng, neLng);
        String query = StringUtils.hasText(name) ? name.trim() : null;
        List<HospitalAccommodation> hospitalsAccommodations =
                hospitalAccommodationRepository.search(null, query, swLat, neLat, swLng, neLng);
        List<Attraction> attractions = attractionRepository.search(query, swLat, neLat, swLng, neLng);

        List<PlaceResponse> combined = new ArrayList<>(hospitalsAccommodations.size() + attractions.size());
        hospitalsAccommodations.stream().map(PlaceResponse::from).forEach(combined::add);
        attractions.stream().map(PlaceResponse::from).forEach(combined::add);
        combined.sort(Comparator.comparing(PlaceResponse::placeName));
        return applyLocale(combined, lang);
    }

    public List<PlaceResponse> listByType(
            PlaceType placeType, String name, String lang,
            BigDecimal swLat, BigDecimal neLat, BigDecimal swLng, BigDecimal neLng) {
        validateBounds(swLat, neLat, swLng, neLng);
        String query = StringUtils.hasText(name) ? name.trim() : null;
        if (placeType == PlaceType.ATTRACTION) {
            List<Attraction> attractions = attractionRepository.search(query, swLat, neLat, swLng, neLng);
            return applyLocale(attractions.stream().map(PlaceResponse::from).toList(), lang);
        }
        List<HospitalAccommodation> places =
                hospitalAccommodationRepository.search(placeType, query, swLat, neLat, swLng, neLng);
        return applyLocale(places.stream().map(PlaceResponse::from).toList(), lang);
    }

    /** swLat/neLat/swLng/neLng는 넷 다 같이 오거나 전부 생략해야 한다 — 일부만 오면 뷰포트를 정할 수 없다. */
    private static void validateBounds(BigDecimal swLat, BigDecimal neLat, BigDecimal swLng, BigDecimal neLng) {
        boolean anyPresent = swLat != null || neLat != null || swLng != null || neLng != null;
        boolean allPresent = swLat != null && neLat != null && swLng != null && neLng != null;
        if (anyPresent && !allPresent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "뷰포트 좌표(swLat/neLat/swLng/neLng)는 넷 다 같이 주거나 전부 생략해야 합니다.");
        }
    }

    public PlaceResponse getOne(Long id, PlaceType placeType, String lang) {
        PlaceResponse response = placeType == PlaceType.ATTRACTION
                ? PlaceResponse.from(findAttractionOrThrow(id))
                : PlaceResponse.from(findHospitalAccommodationOrThrow(id));
        String locale = PlaceTranslationService.normalizeLocale(lang);
        if (locale == null) {
            return response;
        }
        PlaceTranslation translation = placeTranslationService
                .findByPlacesAndLocale(List.of(placeType), List.of(id), locale)
                .get(PlaceTranslationService.key(placeType, id));
        return PlaceResponse.withLocaleOverride(response, translation);
    }

    /** 배치 1건으로 번역을 조회해 얹는다(건별 조회 시 N+1이 되므로). lang이 지원 로케일이 아니면 원본 그대로. */
    private List<PlaceResponse> applyLocale(List<PlaceResponse> responses, String lang) {
        String locale = PlaceTranslationService.normalizeLocale(lang);
        if (locale == null || responses.isEmpty()) {
            return responses;
        }
        List<PlaceType> placeTypes = responses.stream().map(PlaceResponse::placeType).distinct().toList();
        List<Long> placeIds = responses.stream().map(PlaceResponse::id).toList();
        Map<String, PlaceTranslation> translations =
                placeTranslationService.findByPlacesAndLocale(placeTypes, placeIds, locale);
        return responses.stream()
                .map(r -> PlaceResponse.withLocaleOverride(
                        r, translations.get(PlaceTranslationService.key(r.placeType(), r.id()))))
                .toList();
    }

    @Transactional
    public PlaceResponse create(PlaceRequest request) {
        PlaceType placeType = request.placeType() != null ? request.placeType() : PlaceType.ATTRACTION;

        if (placeType == PlaceType.ATTRACTION) {
            Attraction attraction = new Attraction(
                    request.placeId(),
                    null,
                    request.placeName(),
                    request.categoryName(),
                    request.addressName(),
                    request.mapX(),
                    request.mapY(),
                    request.image(),
                    request.phone(),
                    request.placeUrl(),
                    "MANUAL",
                    Instant.now());
            attraction.applyMlTags(
                    request.primaryTypeName(),
                    request.isIndoor(),
                    request.isHeatSource(),
                    request.isMassageSpot(),
                    request.walkHard());
            return PlaceResponse.from(attractionRepository.save(attraction));
        }

        HospitalAccommodation place = new HospitalAccommodation(
                request.placeId(),
                null,
                request.placeName(),
                request.categoryName(),
                request.addressName(),
                request.mapX(),
                request.mapY(),
                request.image(),
                request.phone(),
                request.placeUrl(),
                placeType,
                "MANUAL",
                Instant.now());
        place.applyMlTags(
                request.primaryTypeName(),
                request.skinTreatmentConfidence(),
                request.skinTreatmentSignals());
        return PlaceResponse.from(hospitalAccommodationRepository.save(place));
    }

    @Transactional
    public PlaceResponse update(Long id, PlaceType placeType, PlaceRequest request) {
        if (placeType == PlaceType.ATTRACTION) {
            if (request.placeType() != null && request.placeType() != PlaceType.ATTRACTION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "관광명소는 다른 종류로 바꿀 수 없습니다 (테이블이 달라서 이동이 안 됩니다). 삭제 후 새로 등록해주세요.");
            }
            Attraction attraction = findAttractionOrThrow(id);
            attraction.applyAdminEdit(
                    request.placeName(),
                    request.categoryName(),
                    request.addressName(),
                    request.mapX(),
                    request.mapY(),
                    request.image(),
                    request.phone(),
                    request.placeUrl());
            attraction.applyMlTags(
                    request.primaryTypeName(),
                    request.isIndoor(),
                    request.isHeatSource(),
                    request.isMassageSpot(),
                    request.walkHard());
            return PlaceResponse.from(attraction);
        }

        if (request.placeType() == PlaceType.ATTRACTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "병원/숙소는 관광명소로 바꿀 수 없습니다 (테이블이 달라서 이동이 안 됩니다). 삭제 후 새로 등록해주세요.");
        }
        HospitalAccommodation place = findHospitalAccommodationOrThrow(id);
        place.applyAdminEdit(
                request.placeName(),
                request.categoryName(),
                request.addressName(),
                request.mapX(),
                request.mapY(),
                request.image(),
                request.phone(),
                request.placeUrl(),
                request.placeType());
        place.applyMlTags(
                request.primaryTypeName(),
                request.skinTreatmentConfidence(),
                request.skinTreatmentSignals());
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(Long id, PlaceType placeType) {
        if (placeType == PlaceType.ATTRACTION) {
            if (!attractionRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found: " + id);
            }
            attractionRepository.deleteById(id);
            return;
        }
        if (!hospitalAccommodationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found: " + id);
        }
        hospitalAccommodationRepository.deleteById(id);
    }

    private Attraction findAttractionOrThrow(Long id) {
        return attractionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found: " + id));
    }

    private HospitalAccommodation findHospitalAccommodationOrThrow(Long id) {
        return hospitalAccommodationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found: " + id));
    }
}
