package com.afterglow.web.dto;

import com.afterglow.domain.Place;
import java.math.BigDecimal;
import java.time.Instant;

public record PlaceResponse(
        Long id,
        String placeId,
        String tourismContentId,
        String placeName,
        String categoryName,
        String addressName,
        String roadAddressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image,
        String source,
        boolean imageOverridden,
        Instant syncedAt,
        Place.PlaceType placeType,
        String primaryType,
        String skinTreatmentConfidence,
        String skinTreatmentSignals,
        Boolean isIndoor,
        Boolean isHeatSource,
        Boolean isMassageSpot,
        Integer walkHard,
        Boolean isNa) {

    public static PlaceResponse from(Place place) {
        return new PlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getTourismContentId(),
                place.getPlaceName(),
                place.getCategoryName(),
                place.getAddressName(),
                place.getRoadAddressName(),
                place.getMapX(),
                place.getMapY(),
                place.getImageUrl(),
                place.getSource(),
                place.isImageUrlOverridden(),
                place.getSyncedAt(),
                place.getPlaceType(),
                place.getPrimaryType(),
                place.getSkinTreatmentConfidence(),
                place.getSkinTreatmentSignals(),
                place.getIsIndoor(),
                place.getIsHeatSource(),
                place.getIsMassageSpot(),
                place.getWalkHard(),
                place.getIsNa());
    }
}
