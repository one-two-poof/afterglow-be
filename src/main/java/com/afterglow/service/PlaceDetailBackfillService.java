package com.afterglow.service;

import com.afterglow.domain.HospitalAccommodation;
import com.afterglow.domain.Attraction;
import com.afterglow.domain.PlaceDetail;
import com.afterglow.domain.PlaceType;
import com.afterglow.repository.AttractionRepository;
import com.afterglow.repository.HospitalAccommodationRepository;
import com.afterglow.repository.PlaceDetailRepository;
import com.afterglow.web.dto.MedicalTourismDetail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * tourism_content_id가 있는 행(TourAPI/의료관광 API로 매칭된 행)의 place_details를 채운다.
 * 카카오 단독 소스 행(tourism_content_id 없음)은 대상에서 원천적으로 제외된다.
 *
 * <p>{@link HospitalSyncService}/{@link AccommodationSyncService}/{@link AttractionSyncService} 안에
 * 훅을 넣지 않고 별도 배치로 뺐다 — 동기화 루프마다 신규 행 하나하나에 외부 API 호출이 끼어들면
 * 동기화 자체가 느려지기 때문이다({@link PlaceTranslationBackfillService}와 같은 이유로 같은 구조를
 * 따름). 행마다 외부 HTTP 호출이 있어 스케줄러에서만 호출한다(요청 경로에서 호출하지 않음).
 *
 * <p>메서드 전체를 하나의 트랜잭션으로 묶지 않는다 — 행마다 외부 API를 호출하는데(수백 건일 수 있음)
 * 그 시간 동안 DB 트랜잭션을 계속 열어두는 건 커넥션 점유·타임아웃 위험이 크다. 대신 실제 저장
 * 시점({@link PlaceDetailService#applyDetail})만 건별로 트랜잭션을 짧게 잡는다.
 */
@Service
public class PlaceDetailBackfillService {

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailBackfillService.class);

    private final AttractionRepository attractionRepository;
    private final HospitalAccommodationRepository hospitalAccommodationRepository;
    private final PlaceDetailRepository placeDetailRepository;
    private final PlaceDetailService placeDetailService;
    private final MedicalTourismService medicalTourismService;
    private final TourApiDetailService tourApiDetailService;

    public PlaceDetailBackfillService(
            AttractionRepository attractionRepository,
            HospitalAccommodationRepository hospitalAccommodationRepository,
            PlaceDetailRepository placeDetailRepository,
            PlaceDetailService placeDetailService,
            MedicalTourismService medicalTourismService,
            TourApiDetailService tourApiDetailService) {
        this.attractionRepository = attractionRepository;
        this.hospitalAccommodationRepository = hospitalAccommodationRepository;
        this.placeDetailRepository = placeDetailRepository;
        this.placeDetailService = placeDetailService;
        this.medicalTourismService = medicalTourismService;
        this.tourApiDetailService = tourApiDetailService;
    }

    public BackfillResult backfill() {
        int hospitalFilled = backfillHospitals();
        int accommodationFilled = backfillAccommodations();
        int attractionFilled = backfillAttractions();
        log.info(
                "장소 상세정보 백필 완료: hospital={}건, accommodation={}건, attraction={}건 채움",
                hospitalFilled, accommodationFilled, attractionFilled);
        return new BackfillResult(hospitalFilled, accommodationFilled, attractionFilled);
    }

    private int backfillHospitals() {
        List<HospitalAccommodation> candidates =
                hospitalAccommodationRepository.findByPlaceTypeAndTourismContentIdIsNotNull(PlaceType.HOSPITAL);
        Set<Long> done = alreadyFilled(PlaceType.HOSPITAL, candidates.stream().map(HospitalAccommodation::getId).toList());

        int filled = 0;
        for (HospitalAccommodation place : candidates) {
            if (done.contains(place.getId())) {
                continue;
            }
            try {
                MedicalTourismDetail detail = medicalTourismService.getHospitalDetail(place.getTourismContentId(), null);
                if (!StringUtils.hasText(detail.insttDevInfo())) {
                    continue;
                }
                placeDetailService.applyDetail(
                        PlaceType.HOSPITAL, place.getId(), detail.insttDevInfo(), null,
                        buildHospitalExtraInfo(detail), "MEDICALTOURISM");
                filled++;
            } catch (Exception e) {
                log.warn(
                        "병원 상세정보 백필 실패: placeId={}, contentId={}, error={}",
                        place.getId(), place.getTourismContentId(), e.getMessage());
            }
        }
        return filled;
    }

    private int backfillAccommodations() {
        List<HospitalAccommodation> candidates = hospitalAccommodationRepository
                .findByPlaceTypeAndTourismContentIdIsNotNull(PlaceType.ACCOMMODATION);
        Set<Long> done = alreadyFilled(PlaceType.ACCOMMODATION, candidates.stream().map(HospitalAccommodation::getId).toList());

        int filled = 0;
        for (HospitalAccommodation place : candidates) {
            if (done.contains(place.getId())) {
                continue;
            }
            try {
                TourApiDetailService.Detail detail = tourApiDetailService.fetchAccommodationDetail(place.getTourismContentId());
                if (detail == null) {
                    continue;
                }
                placeDetailService.applyDetail(
                        PlaceType.ACCOMMODATION, place.getId(), detail.overview(), detail.images(),
                        detail.extraInfo(), "TOURAPI");
                filled++;
            } catch (Exception e) {
                log.warn(
                        "숙소 상세정보 백필 실패: placeId={}, contentId={}, error={}",
                        place.getId(), place.getTourismContentId(), e.getMessage());
            }
        }
        return filled;
    }

    private int backfillAttractions() {
        List<Attraction> candidates = attractionRepository.findByTourismContentIdIsNotNull();
        Set<Long> done = alreadyFilled(PlaceType.ATTRACTION, candidates.stream().map(Attraction::getId).toList());

        int filled = 0;
        for (Attraction attraction : candidates) {
            if (done.contains(attraction.getId())) {
                continue;
            }
            try {
                TourApiDetailService.Detail detail = tourApiDetailService.fetchAttractionDetail(attraction.getTourismContentId());
                if (detail == null) {
                    continue;
                }
                placeDetailService.applyDetail(
                        PlaceType.ATTRACTION, attraction.getId(), detail.overview(), detail.images(),
                        detail.extraInfo(), "TOURAPI");
                filled++;
            } catch (Exception e) {
                log.warn(
                        "관광명소 상세정보 백필 실패: placeId={}, contentId={}, error={}",
                        attraction.getId(), attraction.getTourismContentId(), e.getMessage());
            }
        }
        return filled;
    }

    private Set<Long> alreadyFilled(PlaceType placeType, List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Set.of();
        }
        return placeDetailRepository.findByPlaceTypeInAndPlaceIdIn(List.of(placeType), placeIds).stream()
                .map(PlaceDetail::getPlaceId)
                .collect(Collectors.toSet());
    }

    private Map<String, String> buildHospitalExtraInfo(MedicalTourismDetail detail) {
        Map<String, String> extraInfo = new LinkedHashMap<>();
        putIfPresent(extraInfo, "mainSubject", detail.mainMdlcSubjInfo());
        putIfPresent(extraInfo, "specialProcedure", detail.specProcMdlcInfo());
        putIfPresent(extraInfo, "serviceLanguage", detail.svcLangInfo());
        putIfPresent(extraInfo, "homepage", detail.hmpgInfo());
        putIfPresent(extraInfo, "sns", detail.prSnsInfo());
        putIfPresent(extraInfo, "history", detail.histrCn());
        putIfPresent(extraInfo, "onlineReservation", detail.onlineRsvtPsblYn());
        putIfPresent(extraInfo, "consultation", detail.gdsCnselCn());
        putIfPresent(extraInfo, "specialFacility", detail.specFcltyInfo());
        putIfPresent(extraInfo, "cooperativeHospital", detail.corprHsptlInfo());
        putIfPresent(extraInfo, "treatmentGoodsKind", detail.trtmntGdsKndInfo());
        return extraInfo.isEmpty() ? null : extraInfo;
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    public record BackfillResult(int hospitalFilled, int accommodationFilled, int attractionFilled) {
    }
}
