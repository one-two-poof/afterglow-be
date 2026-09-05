package com.afterglow.repository;

import com.afterglow.domain.PlaceDetail;
import com.afterglow.domain.PlaceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceDetailRepository extends JpaRepository<PlaceDetail, Long> {

    Optional<PlaceDetail> findByPlaceTypeAndPlaceId(PlaceType placeType, Long placeId);

    /**
     * 백필 대상에서 이미 처리된 행을 걸러낼 때 쓰는 배치 조회 — 건별 exists 체크는 N+1이 되므로
     * 후보 목록을 한 번에 조회해서 Set으로 걸러낸다.
     */
    List<PlaceDetail> findByPlaceTypeInAndPlaceIdIn(Collection<PlaceType> placeTypes, Collection<Long> placeIds);
}
