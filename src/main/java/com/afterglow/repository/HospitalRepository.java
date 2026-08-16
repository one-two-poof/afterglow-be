package com.afterglow.repository;

import com.afterglow.domain.Hospital;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByPlaceId(String placeId);

    Optional<Hospital> findByTourismContentId(String tourismContentId);

    List<Hospital> findAllByOrderByPlaceNameAsc();

    List<Hospital> findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(String placeName);
}
