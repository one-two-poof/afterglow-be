package com.afterglow.web.dto;

import com.afterglow.domain.Hospital;
import java.math.BigDecimal;
import java.time.Instant;

public record HospitalResponse(
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
        Instant syncedAt) {

    public static HospitalResponse from(Hospital hospital) {
        return new HospitalResponse(
                hospital.getId(),
                hospital.getPlaceId(),
                hospital.getTourismContentId(),
                hospital.getPlaceName(),
                hospital.getCategoryName(),
                hospital.getAddressName(),
                hospital.getRoadAddressName(),
                hospital.getMapX(),
                hospital.getMapY(),
                hospital.getImageUrl(),
                hospital.getSource(),
                hospital.isImageUrlOverridden(),
                hospital.getSyncedAt());
    }
}
