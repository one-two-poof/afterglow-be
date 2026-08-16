package com.afterglow.web.dto;

import java.math.BigDecimal;

public record HospitalRequest(
        String placeId,
        String placeName,
        String categoryName,
        String addressName,
        String roadAddressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image) {
}
