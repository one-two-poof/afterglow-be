package com.afterglow.repository;

import com.afterglow.domain.Place;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByPlaceId(String placeId);

    Optional<Place> findByTourismContentId(String tourismContentId);

    List<Place> findAllByOrderByPlaceNameAsc();

    List<Place> findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(String placeName);
}
