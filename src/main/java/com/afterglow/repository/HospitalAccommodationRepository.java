package com.afterglow.repository;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.PlaceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalAccommodationRepository extends JpaRepository<HospitalAccommodation, Long> {

    Optional<HospitalAccommodation> findByPlaceId(String placeId);

    Optional<HospitalAccommodation> findByTourismContentId(String tourismContentId);

    List<HospitalAccommodation> findAllByOrderByPlaceNameAsc();

    List<HospitalAccommodation> findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(String placeName);

    List<HospitalAccommodation> findByPlaceTypeOrderByPlaceNameAsc(PlaceType placeType);

    List<HospitalAccommodation> findByPlaceTypeAndPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(
            PlaceType placeType, String placeName);
}
