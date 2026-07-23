package com.afterglow.web;

import com.afterglow.service.HospitalService;
import com.afterglow.web.dto.HospitalResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = hospitalService;
    }

    /**
     * Notion 병원 DB 전체 목록 (동기화된 RDB 기준).
     * Notion에서 수정 후 반영: POST /api/sync 또는 스케줄러 대기.
     */
    @GetMapping
    public List<HospitalResponse> listHospitals(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return hospitalService.listAll(includeArchived);
    }
}
