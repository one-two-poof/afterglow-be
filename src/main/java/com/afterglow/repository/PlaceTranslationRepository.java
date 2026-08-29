package com.afterglow.repository;

import com.afterglow.domain.PlaceTranslation;
import com.afterglow.domain.PlaceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceTranslationRepository extends JpaRepository<PlaceTranslation, Long> {

    Optional<PlaceTranslation> findByPlaceTypeAndPlaceIdAndLocale(PlaceType placeType, Long placeId, String locale);

    /**
     * 조회 API 응답에 번역을 얹을 때 쓰는 배치 조회. hospitals_accommodations는 HOSPITAL/ACCOMMODATION이
     * id를 공유하는 한 테이블이라 placeType까지 같이 걸러야 한다(placeId만으로는 어느 테이블 행인지 모호함).
     */
    List<PlaceTranslation> findByPlaceTypeInAndPlaceIdInAndLocale(
            Collection<PlaceType> placeTypes, Collection<Long> placeIds, String locale);
}
