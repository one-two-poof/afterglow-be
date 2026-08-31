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

    /**
     * name/뷰포트(bbox) 필터 모두 선택적이다 — 값이 null인 필터는 조건에서 빠진다.
     * bbox는 네 값(swLat/neLat/swLng/neLng)이 항상 같이 오거나 같이 null이어야 한다(호출부에서 보장).
     */
    @Query("SELECT a FROM Attraction a WHERE "
            + "(:name IS NULL OR LOWER(a.placeName) LIKE LOWER(CONCAT('%', :name, '%'))) AND "
            + "(:swLat IS NULL OR (a.mapY BETWEEN :swLat AND :neLat AND a.mapX BETWEEN :swLng AND :neLng)) "
            + "ORDER BY a.placeName ASC")
    List<Attraction> search(
            @Param("name") String name,
            @Param("swLat") BigDecimal swLat,
            @Param("neLat") BigDecimal neLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLng") BigDecimal neLng);
}
