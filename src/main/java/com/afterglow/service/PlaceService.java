package com.afterglow.service;

import com.afterglow.domain.Place;
import com.afterglow.repository.PlaceRepository;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<PlaceResponse> listAll(String name) {
        List<Place> places = StringUtils.hasText(name)
                ? placeRepository.findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(name.trim())
                : placeRepository.findAllByOrderByPlaceNameAsc();
        return places.stream()
                .map(PlaceResponse::from)
                .toList();
    }

    public List<PlaceResponse> listByType(Place.PlaceType placeType, String name) {
        List<Place> places = StringUtils.hasText(name)
                ? placeRepository.findByPlaceTypeAndPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(
                        placeType, name.trim())
                : placeRepository.findByPlaceTypeOrderByPlaceNameAsc(placeType);
        return places.stream()
                .map(PlaceResponse::from)
                .toList();
    }

    public PlaceResponse getOne(Long id) {
        return PlaceResponse.from(findOrThrow(id));
    }

    @Transactional
    public PlaceResponse create(PlaceRequest request) {
        Place place = new Place(
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
                request.placeType() != null ? request.placeType() : Place.PlaceType.ATTRACTION,
                "MANUAL",
                Instant.now());
        place.applyMlTags(
                request.primaryType(),
                request.primaryTypeName(),
                request.collectionTypes(),
                request.skinTreatmentConfidence(),
                request.skinTreatmentSignals(),
                request.isIndoor(),
                request.isHeatSource(),
                request.isMassageSpot(),
                request.walkHard(),
                request.isNa());
        return PlaceResponse.from(placeRepository.save(place));
    }

    @Transactional
    public PlaceResponse update(Long id, PlaceRequest request) {
        Place place = findOrThrow(id);
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
                request.skinTreatmentSignals(),
                request.isIndoor(),
                request.isHeatSource(),
                request.isMassageSpot(),
                request.walkHard(),
                request.isNa());
        return PlaceResponse.from(place);
    }

    @Transactional
    public void delete(Long id) {
        if (!placeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found: " + id);
        }
        placeRepository.deleteById(id);
    }

    private Place findOrThrow(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Place not found: " + id));
    }
}
