package com.afterglow.web.dto;

import com.afterglow.domain.Attraction;
import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceTranslation;
import com.afterglow.domain.PlaceType;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.util.StringUtils;

public record PlaceResponse(
        Long id,
        String placeId,
        String tourismContentId,
        String placeName,
        String categoryName,
        String addressName,
        BigDecimal mapX,
        BigDecimal mapY,
        String image,
        String phone,
        String placeUrl,
        String source,
        boolean imageOverridden,
        Instant syncedAt,
        PlaceType placeType,
        String primaryTypeName,
        String skinTreatmentConfidence,
        String skinTreatmentSignals,
        Boolean isIndoor,
        Boolean isHeatSource,
        Boolean isMassageSpot,
        Integer walkHard) {

    public static PlaceResponse from(HospitalAccommodation place) {
        return new PlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getTourismContentId(),
                place.getPlaceName(),
                place.getCategoryName(),
                place.getAddressName(),
                place.getMapX(),
                place.getMapY(),
                place.getImageUrl(),
                place.getPhone(),
                place.getPlaceUrl(),
                place.getSource(),
                place.isImageUrlOverridden(),
                place.getSyncedAt(),
                place.getPlaceType(),
                place.getPrimaryTypeName(),
                place.getSkinTreatmentConfidence(),
                place.getSkinTreatmentSignals(),
                null,
                null,
                null,
                null);
    }

    /**
     * lang 파라미터로 번역이 요청됐을 때 place_name/category_name만 갈아 끼운다. 주소는 원문(한국어)을
     * 그대로 유지한다 — 번역하면 택시기사·지도 앱이 못 알아듣는다(place_translations 설계 시 정한 원칙).
     * translation이 null이거나 해당 필드가 아직 안 채워졌으면 원본(base) 값 그대로 둔다.
     */
    public static PlaceResponse withLocaleOverride(PlaceResponse base, PlaceTranslation translation) {
        if (translation == null) {
            return base;
        }
        String placeName = StringUtils.hasText(translation.getPlaceName()) ? translation.getPlaceName() : base.placeName();
        String categoryName = StringUtils.hasText(translation.getCategoryName())
                ? translation.getCategoryName() : base.categoryName();
        return new PlaceResponse(
                base.id(), base.placeId(), base.tourismContentId(), placeName, categoryName,
                base.addressName(), base.mapX(), base.mapY(),
                base.image(), base.phone(), base.placeUrl(), base.source(), base.imageOverridden(), base.syncedAt(),
                base.placeType(), base.primaryTypeName(),
                base.skinTreatmentConfidence(), base.skinTreatmentSignals(), base.isIndoor(), base.isHeatSource(),
                base.isMassageSpot(), base.walkHard());
    }

    public static PlaceResponse from(Attraction place) {
        return new PlaceResponse(
                place.getId(),
                place.getPlaceId(),
                place.getTourismContentId(),
                place.getPlaceName(),
                place.getCategoryName(),
                place.getAddressName(),
                place.getMapX(),
                place.getMapY(),
                place.getImageUrl(),
                place.getPhone(),
                place.getPlaceUrl(),
                place.getSource(),
                place.isImageUrlOverridden(),
                place.getSyncedAt(),
                PlaceType.ATTRACTION,
                place.getPrimaryTypeName(),
                null,
                null,
                place.getIsIndoor(),
                place.getIsHeatSource(),
                place.getIsMassageSpot(),
                place.getWalkHard());
    }
}
