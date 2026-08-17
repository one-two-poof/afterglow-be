package com.afterglow.web.dto;

import com.afterglow.domain.Place;
import java.math.BigDecimal;

public record PlaceRequest(
        String placeId,
        String placeName,
        String categoryName,
        String addressName,
        String roadAddressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image,
        Place.PlaceType placeType) {
}
