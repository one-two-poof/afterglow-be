package com.afterglow.web;

import com.afterglow.service.MedicalTourismService;
import com.afterglow.web.dto.MedicalTourismDetail;
import com.afterglow.web.dto.MedicalTourismListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 한국관광공사 의료관광 정보 API 프록시.
 * 공공데이터포털 응답을 그대로 프론트에 노출한다 (serviceKey는 서버에만 보관).
 */
@RestController
@RequestMapping("/api/medical-tourism")
public class MedicalTourismController {

    private final MedicalTourismService medicalTourismService;

    public MedicalTourismController(MedicalTourismService medicalTourismService) {
        this.medicalTourismService = medicalTourismService;
    }

    /** 의료관광 기관 목록 (페이징) */
    @GetMapping
    public MedicalTourismListResponse list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "100") int numOfRows,
            @RequestParam(required = false) String lang) {
        return medicalTourismService.getHospitals(pageNo, numOfRows, lang);
    }

    /** 특정 기관 상세 (contentId) */
    @GetMapping("/{contentId}")
    public MedicalTourismDetail detail(
            @PathVariable String contentId,
            @RequestParam(required = false) String lang) {
        return medicalTourismService.getHospitalDetail(contentId, lang);
    }
}
