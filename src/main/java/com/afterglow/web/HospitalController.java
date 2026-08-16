package com.afterglow.web;

import com.afterglow.service.HospitalService;
import com.afterglow.service.HospitalSyncService;
import com.afterglow.service.HospitalSyncService.SyncResult;
import com.afterglow.web.dto.HospitalRequest;
import com.afterglow.web.dto.HospitalResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;
    private final HospitalSyncService hospitalSyncService;

    public HospitalController(HospitalService hospitalService, HospitalSyncService hospitalSyncService) {
        this.hospitalService = hospitalService;
        this.hospitalSyncService = hospitalSyncService;
    }

    /**
     * 병원/장소 목록 (관광공사 + 카카오맵 API로 동기화된 RDB 기준).
     * 좌표(mapX/mapY)와 카카오 place_id를 포함해 지도 표시에 바로 쓸 수 있다.
     * name 파라미터를 주면 장소명 부분 일치(대소문자 무시)로 검색한다.
     */
    @GetMapping
    public List<HospitalResponse> listHospitals(@RequestParam(required = false) String name) {
        return hospitalService.listAll(name);
    }

    @GetMapping("/{id}")
    public HospitalResponse getHospital(@PathVariable Long id) {
        return hospitalService.getOne(id);
    }

    /** 로그인(JWT) 필요 — 관리 페이지에서 수동으로 새 장소 추가 */
    @PostMapping
    public ResponseEntity<HospitalResponse> createHospital(@RequestBody HospitalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hospitalService.create(request));
    }

    /** 로그인(JWT) 필요 — 관리 페이지에서 수정 (image는 이후 자동 동기화가 덮어쓰지 않음) */
    @PutMapping("/{id}")
    public HospitalResponse updateHospital(@PathVariable Long id, @RequestBody HospitalRequest request) {
        return hospitalService.update(id, request);
    }

    /** 로그인(JWT) 필요 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHospital(@PathVariable Long id) {
        hospitalService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 로그인(JWT) 필요 — 관광공사+카카오 동기화 수동 트리거 (매일 새벽 자동 실행과 별개) */
    @PostMapping("/sync")
    public SyncResult sync() {
        return hospitalSyncService.sync();
    }
}
