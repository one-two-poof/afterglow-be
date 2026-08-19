package com.afterglow.web;

import com.afterglow.domain.Place;
import com.afterglow.service.HospitalSyncService;
import com.afterglow.service.HospitalSyncService.SyncResult;
import com.afterglow.service.PlaceCsvImportService;
import com.afterglow.service.PlaceCsvImportService.ImportResult;
import com.afterglow.service.PlaceService;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Place", description = "병원·숙소·관광명소를 통합해서 담는 장소 API. 조회는 공개, 쓰기는 JWT 필요")
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
    @Operation(
            summary = "장소 목록 조회",
            description = "병원(관광공사+카카오 동기화)·숙소·관광명소를 통합한 장소 목록. "
                    + "좌표(mapX/mapY)와 카카오 place_id를 포함해 지도 표시에 바로 쓸 수 있다. "
                    + "name을 주면 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping
    public List<PlaceResponse> listPlaces(
            @Parameter(description = "장소명 부분 검색어 (생략 시 전체 목록)")
            @RequestParam(required = false) String name) {
        return placeService.listAll(name);
    }

    @Operation(summary = "장소 단건 조회", description = "id로 장소 1건을 조회한다. 인증 불필요.")
    @GetMapping("/{id}")
    public PlaceResponse getPlace(@PathVariable Long id) {
        return placeService.getOne(id);
    }

    @Operation(
            summary = "병원 목록 조회",
            description = "placeType=HOSPITAL만 필터링한 목록. name을 주면 그 안에서 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/hospital")
    public List<PlaceResponse> listHospitals(
            @Parameter(description = "장소명 부분 검색어 (생략 시 병원 전체)")
            @RequestParam(required = false) String name) {
        return placeService.listByType(Place.PlaceType.HOSPITAL, name);
    }

    @Operation(
            summary = "숙소 목록 조회",
            description = "placeType=ACCOMMODATION만 필터링한 목록. name을 주면 그 안에서 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/accommodation")
    public List<PlaceResponse> listAccommodations(
            @Parameter(description = "장소명 부분 검색어 (생략 시 숙소 전체)")
            @RequestParam(required = false) String name) {
        return placeService.listByType(Place.PlaceType.ACCOMMODATION, name);
    }

    @Operation(
            summary = "관광명소 목록 조회",
            description = "placeType=ATTRACTION만 필터링한 목록. name을 주면 그 안에서 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/attraction")
    public List<PlaceResponse> listAttractions(
            @Parameter(description = "장소명 부분 검색어 (생략 시 관광명소 전체)")
            @RequestParam(required = false) String name) {
        return placeService.listByType(Place.PlaceType.ATTRACTION, name);
    }

    @Operation(
            summary = "장소 수동 추가 (관리자)",
            description = "관리 페이지에서 사람이 직접 새 장소를 추가한다. placeType(HOSPITAL/ACCOMMODATION/ATTRACTION)을 지정해야 한다. JWT 필요.")
    @PostMapping
    public ResponseEntity<PlaceResponse> createPlace(@RequestBody PlaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placeService.create(request));
    }

    @Operation(
            summary = "장소 수동 수정 (관리자)",
            description = "관리 페이지에서 기존 장소를 수정한다. image를 바꾸면 이후 자동 동기화가 그 값을 덮어쓰지 않는다. JWT 필요.")
    @PutMapping("/{id}")
    public PlaceResponse updatePlace(@PathVariable Long id, @RequestBody PlaceRequest request) {
        return placeService.update(id, request);
    }

    @Operation(summary = "장소 삭제 (관리자)", description = "id로 장소 1건을 삭제한다. JWT 필요.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlace(@PathVariable Long id) {
        placeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "병원 동기화 수동 트리거",
            description = "한국관광공사 의료관광 목록과 카카오 로컬 API(HP8 병원 카테고리, 강남/서초 스윕)를 조합해 "
                    + "place 테이블의 HOSPITAL 행을 갱신한다. 매일 새벽 4시 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
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
    @Operation(
            summary = "CSV로 장소 일괄 반영 (관리자)",
            description = "data/raw/ml_data의 CSV를 kakaoPlaceId 기준으로 place 테이블에 반영한다. "
                    + "createIfMissing=false(기본)면 이미 동기화로 들어와 있는 행에 ML 태그 필드만 채우는 백필 전용. "
                    + "createIfMissing=true면 매칭 안 되는 행을 새로 만들고, 이때 placeType이 새 행에 적용된다. JWT 필요.")
    @PostMapping("/import-csv")
    public ImportResult importCsv(
            @Parameter(description = "CSV 파일 경로 (리포 루트 기준 상대경로). 생략 시 병원 ML 태그 CSV를 백필용으로 사용")
            @RequestParam(required = false) String path,
            @Parameter(description = "true면 매칭 안 되는 행을 새로 생성한다 (숙소·관광명소 CSV처럼 살아있는 동기화 소스가 없는 경우)")
            @RequestParam(required = false, defaultValue = "false") boolean createIfMissing,
            @Parameter(description = "createIfMissing=true일 때 새로 생성되는 행에 부여할 종류")
            @RequestParam(required = false, defaultValue = "HOSPITAL") Place.PlaceType placeType) {
        Path csvPath = Path.of(path != null ? path : DEFAULT_HOSPITAL_ML_TAG_CSV_PATH);
        return placeCsvImportService.importFrom(csvPath, createIfMissing, placeType);
    }
}
