package com.afterglow.repository;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HospitalAccommodationRepository extends JpaRepository<HospitalAccommodation, Long> {

    Optional<HospitalAccommodation> findByPlaceId(String placeId);

    Optional<HospitalAccommodation> findByTourismContentId(String tourismContentId);

    /** place_details 백필 대상 후보 조회 — tourism_content_id가 있는 행만 상세 API를 부를 수 있다. */
    List<HospitalAccommodation> findByPlaceTypeAndTourismContentIdIsNotNull(PlaceType placeType);

    /**
     * placeType/name/뷰포트(bbox) 세 필터 모두 선택적이다 — 값이 null인 필터는 조건에서 빠진다.
     * bbox는 네 값(swLat/neLat/swLng/neLng)이 항상 같이 오거나 같이 null이어야 한다(호출부에서 보장).
     * 지도 마커 조회용이라 정렬은 하지 않는다 — 이름순 정렬은 마커 표시에 의미가 없고 정렬 비용만 붙는다.
     */
    @Query("SELECT h FROM HospitalAccommodation h WHERE "
            + "(:placeType IS NULL OR h.placeType = :placeType) AND "
            + "(:name IS NULL OR LOWER(h.placeName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND "
            + "(:swLat IS NULL OR (h.mapY BETWEEN :swLat AND :neLat AND h.mapX BETWEEN :swLng AND :neLng))")
    List<HospitalAccommodation> search(
            @Param("placeType") PlaceType placeType,
            @Param("name") String name,
            @Param("swLat") BigDecimal swLat,
            @Param("neLat") BigDecimal neLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLng") BigDecimal neLng);
}
