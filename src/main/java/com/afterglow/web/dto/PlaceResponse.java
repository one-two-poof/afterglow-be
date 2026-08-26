package com.afterglow.web.dto;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import java.math.BigDecimal;
import java.time.Instant;

public record PlaceResponse(
        Long id,
        String placeId,
        String tourismContentId,
        String placeName,
        String categoryName,
        String categoryGroupName,
        String addressName,
        String roadAddressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image,
        String phone,
        String placeUrl,
        String source,
        boolean imageOverridden,
        Instant syncedAt,
        PlaceType placeType,
        String primaryType,
        String primaryTypeName,
        String collectionTypes,
        String skinTreatmentConfidence,
        String skinTreatmentSignals,
        Boolean isIndoor,
        Boolean isHeatSource,
        Boolean isMassageSpot,
        Integer walkHard,
        Boolean isNa) {

    public static PlaceResponse from(HospitalAccommodation place) {
        return new PlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getTourismContentId(),
                place.getPlaceName(),
                place.getCategoryName(),
                place.getCategoryGroupName(),
                place.getAddressName(),
                place.getRoadAddressName(),
                place.getMapX(),
                place.getMapY(),
                place.getImageUrl(),
                place.getPhone(),
                place.getPlaceUrl(),
                place.getSource(),
                place.isImageUrlOverridden(),
                place.getSyncedAt(),
                place.getPlaceType(),
                place.getPrimaryType(),
                place.getPrimaryTypeName(),
                place.getCollectionTypes(),
                place.getSkinTreatmentConfidence(),
                place.getSkinTreatmentSignals(),
                null,
                null,
                null,
                null,
                null);
    }

    public static PlaceResponse from(Attraction place) {
        return new PlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getTourismContentId(),
                place.getPlaceName(),
                place.getCategoryName(),
                place.getCategoryGroupName(),
                place.getAddressName(),
                place.getRoadAddressName(),
                place.getMapX(),
                place.getMapY(),
                place.getImageUrl(),
                place.getPhone(),
                place.getPlaceUrl(),
                place.getSource(),
                place.isImageUrlOverridden(),
                place.getSyncedAt(),
                PlaceType.ATTRACTION,
                place.getPrimaryType(),
                place.getPrimaryTypeName(),
                place.getCollectionTypes(),
                null,
                null,
                place.getIsIndoor(),
                place.getIsHeatSource(),
                place.getIsMassageSpot(),
                place.getWalkHard(),
                place.getIsNa());
    }
}
