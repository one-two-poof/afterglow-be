package com.afterglow.service;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public PlaceService(
            HospitalAccommodationRepository hospitalAccommodationRepository,
            AttractionRepository attractionRepository) {
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.attractionRepository = attractionRepository;
    }

    public List<PlaceResponse> listAll(String name) {
        List<HospitalAccommodation> hospitalsAccommodations = StringUtils.hasText(name)
                ? hospitalAccommodationRepository.findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(name.trim())
                : hospitalAccommodationRepository.findAllByOrderByPlaceNameAsc();
        List<Attraction> attractions = StringUtils.hasText(name)
                ? attractionRepository.findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(name.trim())
                : attractionRepository.findAllByOrderByPlaceNameAsc();

        List<PlaceResponse> combined = new ArrayList<>(hospitalsAccommodations.size() + attractions.size());
        hospitalsAccommodations.stream().map(PlaceResponse::from).forEach(combined::add);
        attractions.stream().map(PlaceResponse::from).forEach(combined::add);
        combined.sort(Comparator.comparing(PlaceResponse::placeName));
        return combined;
    }

    public List<PlaceResponse> listByType(PlaceType placeType, String name) {
        if (placeType == PlaceType.ATTRACTION) {
            List<Attraction> attractions = StringUtils.hasText(name)
                    ? attractionRepository.findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(name.trim())
                    : attractionRepository.findAllByOrderByPlaceNameAsc();
            return attractions.stream().map(PlaceResponse::from).toList();
        }
        List<HospitalAccommodation> places = StringUtils.hasText(name)
                ? hospitalAccommodationRepository.findByPlaceTypeAndPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(
                        placeType, name.trim())
                : hospitalAccommodationRepository.findByPlaceTypeOrderByPlaceNameAsc(placeType);
        return places.stream().map(PlaceResponse::from).toList();
    }

    public PlaceResponse getOne(Long id, PlaceType placeType) {
        return placeType == PlaceType.ATTRACTION
                ? PlaceResponse.from(findAttractionOrThrow(id))
                : PlaceResponse.from(findHospitalAccommodationOrThrow(id));
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
                    request.roadAddressName(),
                    request.mapX(),
                    request.mapY(),
                    request.image(),
                    null,
                    request.categoryGroupName(),
                    request.phone(),
                    request.placeUrl(),
                    "MANUAL",
                    Instant.now());
            attraction.applyMlTags(
                    request.primaryType(),
                    request.primaryTypeName(),
                    request.collectionTypes(),
                    request.isIndoor(),
                    request.isHeatSource(),
                    request.isMassageSpot(),
                    request.walkHard(),
                    request.isNa());
            return PlaceResponse.from(attractionRepository.save(attraction));
        }

        HospitalAccommodation place = new HospitalAccommodation(
                request.placeId(),
                null,
                request.placeName(),
                request.categoryName(),
                request.addressName(),
                request.roadAddressName(),
                request.mapX(),
                request.mapY(),
                request.image(),
                null,
                request.categoryGroupName(),
                request.phone(),
                request.placeUrl(),
                placeType,
                "MANUAL",
                Instant.now());
        place.applyMlTags(
                request.primaryType(),
                request.primaryTypeName(),
                request.collectionTypes(),
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
                    request.categoryGroupName(),
                    request.addressName(),
                    request.roadAddressName(),
                    request.mapX(),
                    request.mapY(),
                    request.image(),
                    request.phone(),
                    request.placeUrl());
            attraction.applyMlTags(
                    request.primaryType(),
                    request.primaryTypeName(),
                    request.collectionTypes(),
                    request.isIndoor(),
                    request.isHeatSource(),
                    request.isMassageSpot(),
                    request.walkHard(),
                    request.isNa());
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
                request.categoryGroupName(),
                request.addressName(),
                request.roadAddressName(),
                request.mapX(),
                request.mapY(),
                request.image(),
                request.phone(),
                request.placeUrl(),
                request.placeType());
        place.applyMlTags(
                request.primaryType(),
                request.primaryTypeName(),
                request.collectionTypes(),
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
