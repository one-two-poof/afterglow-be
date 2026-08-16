package com.afterglow.service;

import com.afterglow.domain.Hospital;
import com.afterglow.repository.HospitalRepository;
import com.afterglow.web.dto.HospitalRequest;
import com.afterglow.web.dto.HospitalResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HospitalService {

    private final HospitalRepository hospitalRepository;

    public HospitalService(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    public List<HospitalResponse> listAll(String name) {
        List<Hospital> hospitals = StringUtils.hasText(name)
                ? hospitalRepository.findByPlaceNameContainingIgnoreCaseOrderByPlaceNameAsc(name.trim())
                : hospitalRepository.findAllByOrderByPlaceNameAsc();
        return hospitals.stream()
                .map(HospitalResponse::from)
                .toList();
    }

    public HospitalResponse getOne(Long id) {
        return HospitalResponse.from(findOrThrow(id));
    }

    @Transactional
    public HospitalResponse create(HospitalRequest request) {
        Hospital hospital = new Hospital(
                request.placeId(),
                null,
                request.placeName(),
                request.categoryName(),
                request.addressName(),
                request.roadAddressName(),
                request.mapX(),
                request.mapY(),
                request.image(),
                "MANUAL",
                Instant.now());
        return HospitalResponse.from(hospitalRepository.save(hospital));
    }

    @Transactional
    public HospitalResponse update(Long id, HospitalRequest request) {
        Hospital hospital = findOrThrow(id);
        hospital.applyAdminEdit(
                request.placeName(),
                request.categoryName(),
                request.addressName(),
                request.roadAddressName(),
                request.mapX(),
                request.mapY(),
                request.image());
        return HospitalResponse.from(hospital);
    }

    @Transactional
    public void delete(Long id) {
        if (!hospitalRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found: " + id);
        }
        hospitalRepository.deleteById(id);
    }

    private Hospital findOrThrow(Long id) {
        return hospitalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hospital not found: " + id));
    }
}
