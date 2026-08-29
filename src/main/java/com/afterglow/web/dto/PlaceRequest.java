package com.afterglow.web.dto;

import com.afterglow.domain.PlaceType;
import java.math.BigDecimal;

public record PlaceRequest(
        String placeId,
        String placeName,
        String categoryName,
        String addressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image,
        String phone,
        String placeUrl,
        String primaryTypeName,
        String skinTreatmentConfidence,
        String skinTreatmentSignals,
        Boolean isIndoor,
        Boolean isHeatSource,
        Boolean isMassageSpot,
        Integer walkHard,
        PlaceType placeType) {
}
