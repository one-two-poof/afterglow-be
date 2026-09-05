package com.afterglow.repository;

import com.afterglow.domain.Attraction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    Optional<Attraction> findByPlaceId(String placeId);

    Optional<Attraction> findByTourismContentId(String tourismContentId);

    /** place_details 백필 대상 후보 조회 — tourism_content_id가 있는 행만 상세 API를 부를 수 있다. */
    List<Attraction> findByTourismContentIdIsNotNull();

    /**
     * name/뷰포트(bbox) 필터 모두 선택적이다 — 값이 null인 필터는 조건에서 빠진다.
     * bbox는 네 값(swLat/neLat/swLng/neLng)이 항상 같이 오거나 같이 null이어야 한다(호출부에서 보장).
     * 지도 마커 조회용이라 정렬은 하지 않는다 — 이름순 정렬은 마커 표시에 의미가 없고 정렬 비용만 붙는다.
     */
    @Query("SELECT a FROM Attraction a WHERE "
            + "(:name IS NULL OR LOWER(a.placeName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND "
            + "(:swLat IS NULL OR (a.mapY BETWEEN :swLat AND :neLat AND a.mapX BETWEEN :swLng AND :neLng))")
    List<Attraction> search(
            @Param("name") String name,
            @Param("swLat") BigDecimal swLat,
            @Param("neLat") BigDecimal neLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLng") BigDecimal neLng);
}
