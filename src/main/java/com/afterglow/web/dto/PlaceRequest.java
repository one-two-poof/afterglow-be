package com.afterglow.web.dto;

import com.afterglow.domain.PlaceType;
import java.math.BigDecimal;

public record PlaceRequest(
        String placeId,
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
        String primaryType,
        String primaryTypeName,
        String collectionTypes,
        String skinTreatmentConfidence,
        String skinTreatmentSignals,
        Boolean isIndoor,
        Boolean isHeatSource,
        Boolean isMassageSpot,
        Integer walkHard,
        Boolean isNa,
        PlaceType placeType) {
}
