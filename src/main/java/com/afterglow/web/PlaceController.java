package com.afterglow.web;

import com.afterglow.domain.PlaceType;
import com.afterglow.service.AccommodationSyncService;
import com.afterglow.service.AttractionSyncService;
import com.afterglow.service.HospitalSyncService;
import com.afterglow.service.HospitalSyncService.SyncResult;
import com.afterglow.service.PlaceDetailBackfillService;
import com.afterglow.service.PlaceService;
import com.afterglow.service.PlaceTranslationBackfillService;
import com.afterglow.service.PlaceTranslationBackfillService.BackfillResult;
import com.afterglow.web.dto.PlaceRequest;
import com.afterglow.web.dto.PlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
@Tag(name = "Place", description = "병원·숙소는 hospitals_accommodations, 관광명소는 attractions 테이블에 나뉘어 저장된다. "
        + "조회는 공개, 쓰기는 JWT 필요")
public class PlaceController {

    private static final String LANG_DESCRIPTION =
            "응답 언어. 'ja' 또는 'en'만 지원하며, 생략하거나 다른 값을 주면 한국어 원본을 그대로 반환한다. "
                    + "place_translations에 아직 번역이 없는 필드는 한국어 원본으로 폴백한다. 주소는 로케일과 무관하게 항상 한국어 원본.";
    private static final String BBOX_DESCRIPTION =
            "지도 뷰포트(현재 화면에 보이는 영역)로 결과를 제한한다. swLat/neLat/swLng/neLng 넷 다 같이 주거나 "
                    + "전부 생략해야 하며(일부만 주면 400), 생략 시 필터 없이 전체를 반환한다.";

    private final PlaceService placeService;
    private final HospitalSyncService hospitalSyncService;
    private final AccommodationSyncService accommodationSyncService;
    private final AttractionSyncService attractionSyncService;
    private final PlaceTranslationBackfillService placeTranslationBackfillService;
    private final PlaceDetailBackfillService placeDetailBackfillService;

    public PlaceController(
            PlaceService placeService,
            HospitalSyncService hospitalSyncService,
            AccommodationSyncService accommodationSyncService,
            AttractionSyncService attractionSyncService,
            PlaceTranslationBackfillService placeTranslationBackfillService,
            PlaceDetailBackfillService placeDetailBackfillService) {
        this.placeService = placeService;
        this.hospitalSyncService = hospitalSyncService;
        this.accommodationSyncService = accommodationSyncService;
        this.attractionSyncService = attractionSyncService;
        this.placeTranslationBackfillService = placeTranslationBackfillService;
        this.placeDetailBackfillService = placeDetailBackfillService;
    }

    @Operation(
            summary = "장소 목록 조회",
            description = "병원·숙소(hospitals_accommodations)와 관광명소(attractions) 두 테이블을 합친 목록. "
                    + "좌표(mapX/mapY)와 카카오 place_id를 포함해 지도 표시에 바로 쓸 수 있다. "
                    + "name을 주면 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping
    public List<PlaceResponse> listPlaces(
            @Parameter(description = "장소명 부분 검색어 (생략 시 전체 목록)")
            @RequestParam(required = false) String name,
            @Parameter(description = LANG_DESCRIPTION)
            @RequestParam(required = false) String lang,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLng,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLng) {
        return placeService.listAll(name, lang, swLat, neLat, swLng, neLng);
    }

    @Operation(
            summary = "장소 단건 조회",
            description = "id와 placeType으로 장소 1건을 조회한다. id는 hospitals_accommodations/attractions 두 테이블에서 "
                    + "각자 독립적으로 채번되므로 placeType 없이는 어느 테이블 행인지 알 수 없다. "
                    + "tourism_content_id가 있는 행(TourAPI/의료관광 API로 매칭된 행)이면 overview/images/extraInfo에 "
                    + "소개글·이미지·운영정보가 채워진다(place_details 백필 완료 전이거나 카카오 단독 소스 행이면 셋 다 null). "
                    + "목록 조회(listPlaces 등)엔 이 필드들이 포함되지 않는다. 인증 불필요.")
    @GetMapping("/{id}")
    public PlaceResponse getPlace(
            @PathVariable Long id,
            @Parameter(description = "이 id가 속한 테이블을 정한다 (HOSPITAL/ACCOMMODATION → hospitals_accommodations, ATTRACTION → attractions)")
            @RequestParam PlaceType placeType,
            @Parameter(description = LANG_DESCRIPTION)
            @RequestParam(required = false) String lang) {
        return placeService.getOne(id, placeType, lang);
    }

    @Operation(
            summary = "병원 목록 조회",
            description = "hospitals_accommodations에서 placeType=HOSPITAL만 필터링한 목록. name을 주면 그 안에서 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/hospital")
    public List<PlaceResponse> listHospitals(
            @Parameter(description = "장소명 부분 검색어 (생략 시 병원 전체)")
            @RequestParam(required = false) String name,
            @Parameter(description = LANG_DESCRIPTION)
            @RequestParam(required = false) String lang,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLng,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLng) {
        return placeService.listByType(PlaceType.HOSPITAL, name, lang, swLat, neLat, swLng, neLng);
    }

    @Operation(
            summary = "숙소 목록 조회",
            description = "hospitals_accommodations에서 placeType=ACCOMMODATION만 필터링한 목록. name을 주면 그 안에서 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/accommodation")
    public List<PlaceResponse> listAccommodations(
            @Parameter(description = "장소명 부분 검색어 (생략 시 숙소 전체)")
            @RequestParam(required = false) String name,
            @Parameter(description = LANG_DESCRIPTION)
            @RequestParam(required = false) String lang,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLng,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLng) {
        return placeService.listByType(PlaceType.ACCOMMODATION, name, lang, swLat, neLat, swLng, neLng);
    }

    @Operation(
            summary = "관광명소 목록 조회",
            description = "attractions 테이블 전체. name을 주면 장소명 부분 일치(대소문자 무시)로 검색한다. 인증 불필요.")
    @GetMapping("/attraction")
    public List<PlaceResponse> listAttractions(
            @Parameter(description = "장소명 부분 검색어 (생략 시 관광명소 전체)")
            @RequestParam(required = false) String name,
            @Parameter(description = LANG_DESCRIPTION)
            @RequestParam(required = false) String lang,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLat,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal swLng,
            @Parameter(description = BBOX_DESCRIPTION) @RequestParam(required = false) BigDecimal neLng) {
        return placeService.listByType(PlaceType.ATTRACTION, name, lang, swLat, neLat, swLng, neLng);
    }

    @Operation(
            summary = "장소 수동 추가 (관리자)",
            description = "관리 페이지에서 사람이 직접 새 장소를 추가한다. placeType(HOSPITAL/ACCOMMODATION/ATTRACTION)에 따라 "
                    + "hospitals_accommodations 또는 attractions 중 어디에 들어갈지 정해진다. JWT 필요.")
    @PostMapping
    public ResponseEntity<PlaceResponse> createPlace(@RequestBody PlaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placeService.create(request));
    }

    @Operation(
            summary = "장소 수동 수정 (관리자)",
            description = "id와 placeType으로 대상을 정해 수정한다. image를 바꾸면 이후 자동 동기화가 그 값을 덮어쓰지 않는다. "
                    + "테이블이 다른 종류로는 못 바꾼다(예: 관광명소 → 병원). JWT 필요.")
    @PutMapping("/{id}")
    public PlaceResponse updatePlace(
            @PathVariable Long id,
            @Parameter(description = "이 id가 속한 테이블 (수정 대상을 찾는 데 씀)")
            @RequestParam PlaceType placeType,
            @RequestBody PlaceRequest request) {
        return placeService.update(id, placeType, request);
    }

    @Operation(
            summary = "장소 삭제 (관리자)",
            description = "id와 placeType으로 대상을 정해 삭제한다. JWT 필요.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlace(
            @PathVariable Long id,
            @Parameter(description = "이 id가 속한 테이블 (삭제 대상을 찾는 데 씀)")
            @RequestParam PlaceType placeType) {
        placeService.delete(id, placeType);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "병원 동기화 수동 트리거",
            description = "한국관광공사 의료관광 목록과 카카오 로컬 API를 조합해 hospitals_accommodations 테이블의 "
                    + "HOSPITAL 행을 서울 전체 범위로 갱신한다. 추가로 피부시술 키워드(리프팅/보톡스 등 10개)로 서울 25개 구를 "
                    + "훑어 skinTreatmentConfidence/skinTreatmentSignals를 채운다. 매일 새벽 4시 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
    @PostMapping("/sync-hospitals")
    public SyncResult syncHospitals() {
        return hospitalSyncService.sync();
    }

    @Operation(
            summary = "숙소 동기화 수동 트리거",
            description = "한국관광공사 TourAPI(KorService2, 숙박)와 카카오 로컬 API(AD5 숙박 카테고리)를 조합해 "
                    + "서울 전체 범위로 hospitals_accommodations 테이블의 ACCOMMODATION 행을 갱신한다. "
                    + "추가로 TourAPI 목록과 무관하게 카카오 AD5 그룹코드로 서울 25개 구를 직접 스윕해 "
                    + "TourAPI에 없는 숙소(게스트하우스 등)도 찾아 채운다. "
                    + "매일 새벽 4시 30분 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
    @PostMapping("/sync-accommodations")
    public AccommodationSyncService.SyncResult syncAccommodations() {
        return accommodationSyncService.sync();
    }

    @Operation(
            summary = "관광명소 동기화 수동 트리거",
            description = "한국관광공사 TourAPI(KorService2, 관광지/문화시설/쇼핑)와 카카오 로컬 API를 조합해 "
                    + "서울 전체 범위로 attractions 테이블을 갱신한다. 추가로 카카오 카테고리 그룹(CE7/CT1/AT4)·키워드로 "
                    + "서울 25개 구를 훑어 카페/드럭스토어/백화점/쇼핑몰/미술관/공연장/찜질방/안마·스파 등을 분류해 채운다"
                    + "(AttractionClassifier, 일부 유형은 popularity 최소 기준 미달 시 제외). "
                    + "매일 새벽 4시 45분 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
    @PostMapping("/sync-attractions")
    public AttractionSyncService.SyncResult syncAttractions() {
        return attractionSyncService.sync();
    }

    @Operation(
            summary = "번역 백필 수동 트리거 (관리자)",
            description = "TourAPI/의료관광 API 공식 번역이 없는 행(카카오 단독 소스 등)의 place_name/category_name "
                    + "빈 자리를 SeedTranslationProvider로 채운다. 매일 새벽 5시 30분 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
    @PostMapping("/backfill-translations")
    public BackfillResult backfillTranslations() {
        return placeTranslationBackfillService.backfill();
    }

    @Operation(
            summary = "장소 상세정보(overview/images/extraInfo) 백필 수동 트리거 (관리자)",
            description = "tourism_content_id가 있는 행(병원은 의료관광 API, 숙소·관광명소는 TourAPI로 매칭된 행)의 "
                    + "소개글/이미지/운영정보를 place_details에 채운다. 관광명소는 contentTypeId를 DB에 저장하지 않아 "
                    + "운영정보(extraInfo)는 항상 비고 소개글/이미지만 채워진다(docs/place-detail-info-plan.md 참고). "
                    + "매일 새벽 5시 자동 실행되는 것과 같은 로직을 즉시 실행. JWT 필요.")
    @PostMapping("/backfill-details")
    public PlaceDetailBackfillService.BackfillResult backfillDetails() {
        return placeDetailBackfillService.backfill();
    }
}
