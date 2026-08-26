package com.afterglow.repository;

import com.afterglow.domain.Attraction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    Optional<Attraction> findByPlaceId(String placeId);

    Optional<Attraction> findByTourismContentId(String tourismContentId);

    List<Attraction> findAllByOrderByPlaceNameAsc();

    List<Attraction> findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(String placeName);
}
