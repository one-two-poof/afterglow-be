package com.afterglow.web;

import com.afterglow.service.MedicalTourismService;
import com.afterglow.web.dto.MedicalTourismDetail;
import com.afterglow.web.dto.MedicalTourismListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "MedicalTourism", description = "한국관광공사 의료관광 API 프록시 (원본 응답 그대로 전달, serviceKey는 서버에만 보관). 인증 불필요")
public class MedicalTourismController {

    private final MedicalTourismService medicalTourismService;

    public MedicalTourismController(MedicalTourismService medicalTourismService) {
        this.medicalTourismService = medicalTourismService;
    }

    @Operation(summary = "의료관광 기관 목록 (페이징)", description = "관광공사 원본 목록 API를 그대로 프록시한다.")
    @GetMapping
    public MedicalTourismListResponse list(
            @Parameter(description = "페이지 번호 (1부터)") @RequestParam(defaultValue = "1") int pageNo,
            @Parameter(description = "페이지당 개수") @RequestParam(defaultValue = "100") int numOfRows,
            @Parameter(description = "언어 코드 (예: KOR, ENG). 생략 시 서비스 기본값") @RequestParam(required = false) String lang) {
        return medicalTourismService.getHospitals(pageNo, numOfRows, lang);
    }

    @Operation(summary = "의료관광 기관 상세", description = "contentId로 관광공사 원본 상세 정보를 조회한다.")
    @GetMapping("/{contentId}")
    public MedicalTourismDetail detail(
            @PathVariable String contentId,
            @Parameter(description = "언어 코드 (예: KOR, ENG). 생략 시 서비스 기본값") @RequestParam(required = false) String lang) {
        return medicalTourismService.getHospitalDetail(contentId, lang);
    }
}
