package com.afterglow.web;

import com.afterglow.domain.Place;
import com.afterglow.service.HospitalSyncService;
import com.afterglow.service.HospitalSyncService.SyncResult;
import com.afterglow.service.PlaceCsvImportService;
import com.afterglow.service.PlaceCsvImportService.ImportResult;
import com.afterglow.service.PlaceService;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import java.nio.file.Path;
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
@RequestMapping("/api/places")
public class PlaceController {

    private static final String DEFAULT_HOSPITAL_ML_TAG_CSV_PATH =
            "data/raw/ml_data/gangnam_seocho_hospital_db.csv";

    private final PlaceService placeService;
    private final HospitalSyncService hospitalSyncService;
    private final PlaceCsvImportService placeCsvImportService;

    public PlaceController(
            PlaceService placeService,
            HospitalSyncService hospitalSyncService,
            PlaceCsvImportService placeCsvImportService) {
        this.placeService = placeService;
        this.hospitalSyncService = hospitalSyncService;
        this.placeCsvImportService = placeCsvImportService;
    }

    /**
     * 장소 목록 (병원은 관광공사+카카오, 숙소 등은 CSV import로 채워지는 통합 RDB 기준).
     * 좌표(mapX/mapY)와 카카오 place_id를 포함해 지도 표시에 바로 쓸 수 있다.
     * name 파라미터를 주면 장소명 부분 일치(대소문자 무시)로 검색한다.
     */
    @GetMapping
    public List<PlaceResponse> listPlaces(@RequestParam(required = false) String name) {
        return placeService.listAll(name);
    }

    @GetMapping("/{id}")
    public PlaceResponse getPlace(@PathVariable Long id) {
        return placeService.getOne(id);
    }

    /** 로그인(JWT) 필요 — 관리 페이지에서 수동으로 새 장소 추가 */
    @PostMapping
    public ResponseEntity<PlaceResponse> createPlace(@RequestBody PlaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placeService.create(request));
    }

    /** 로그인(JWT) 필요 — 관리 페이지에서 수정 (image는 이후 자동 동기화가 덮어쓰지 않음) */
    @PutMapping("/{id}")
    public PlaceResponse updatePlace(@PathVariable Long id, @RequestBody PlaceRequest request) {
        return placeService.update(id, request);
    }

    /** 로그인(JWT) 필요 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlace(@PathVariable Long id) {
        placeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** 로그인(JWT) 필요 — 병원 전용: 관광공사+카카오 동기화 수동 트리거 (매일 새벽 자동 실행과 별개) */
    @PostMapping("/sync-hospitals")
    public SyncResult syncHospitals() {
        return hospitalSyncService.sync();
    }

    /**
     * 로그인(JWT) 필요 — CSV를 kakaoPlaceId 기준으로 place 테이블에 반영.
     * path 생략 시 병원 ML 태그 CSV를 백필 전용(createIfMissing=false)으로 처리.
     * 문화시설/백화점/드럭스토어/관광명소처럼 살아있는 동기화 소스가 없는 카테고리
     * (예: data/raw/ml_data/gangnam_seocho_places_with_constraints.csv, 도보 제약
     * isIndoor/isHeatSource/isMassageSpot/walkHard/isNa 포함)는 path와
     * createIfMissing=true, placeType을 명시해서 새 행도 만들도록 호출한다.
     * placeType은 createIfMissing=true일 때 새로 생성되는 행에만 쓰인다(기존 행 매칭 시엔 무시).
     */
    @PostMapping("/import-csv")
    public ImportResult importCsv(
            @RequestParam(required = false) String path,
            @RequestParam(required = false, defaultValue = "false") boolean createIfMissing,
            @RequestParam(required = false, defaultValue = "HOSPITAL") Place.PlaceType placeType) {
        Path csvPath = Path.of(path != null ? path : DEFAULT_HOSPITAL_ML_TAG_CSV_PATH);
        return placeCsvImportService.importFrom(csvPath, createIfMissing, placeType);
    }
}
