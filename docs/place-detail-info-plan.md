# 장소 상세정보(overview/운영정보) 확장 — 실행 계획

> ✅ 구현 완료(phase 1: 2026-09-05, phase 2/관광명소 운영정보: 2026-09-06). 최종 결과/응답 형태는
> `docs/place-detail-page-guide.md` 7절 참고. 이 문서는 설계 배경(왜 이렇게 나눴는지, contentTypeId
> 문제를 어떻게 풀었는지)을 남겨두는 용도로 유지한다.

## 목표

병원/숙소/관광명소 상세 페이지에서 지금 `PlaceResponse`엔 없는 "소개글(overview)", "이미지 갤러리",
"운영정보(체크인/체크아웃, 이용시간, 부대시설 등)"를 추가로 보여준다.

**확정된 결정 2가지** (사용자 지시):
1. 기존 테이블(`hospitals_accommodations`, `attractions`)은 건드리지 않는다 — 새 테이블 하나를 추가한다.
2. 응답은 별도 엔드포인트가 아니라 기존 `GET /api/places/{id}` 응답에 필드로 얹어서 준다. 목록 API
   (`/api/places`, `/api/places/hospital` 등)는 건드리지 않는다(payload 무거워지는 것 방지).

## 0. 전제 — 이미 확인된 제약

- `tourism_content_id`가 있는 행만 대상이 될 수 있다. `source=KAKAO` 단독 행(카카오 시술/AD5/CE7
  스윕으로 생성된 행)은 애초에 `tourism_content_id`가 없어서 이 기능의 대상이 될 수 없다 —
  프론트/백엔드 모두 이 케이스를 "정보 없음"으로 정상 처리해야 한다.
- 병원은 `HospitalSyncService`가 채우는 `tourism_content_id`가 **의료관광 API**(`MdclTursmService`)
  contentId이고, 숙소/관광명소는 **TourAPI KorService2** contentId다 — 서로 다른 API를 호출해야 한다.
- ⚠️→✅ **관광명소(ATTRACTION)의 제약(해결됨)**: TourAPI 상세 조회(`detailIntro2`)는
  `contentTypeId`(12=관광지/14=문화시설/38=쇼핑)를 파라미터로 요구하는데, `Attraction` 엔티티엔
  이 값이 저장돼 있지 않다(`AttractionSyncService`의 `CategoryConfig.contentTypeId()`는 동기화
  중에만 쓰이는 휘발성 값). `attractions` 테이블은 안 건드리기로 했으므로, 대신 **`place_details`
  에 `content_type_id` 컬럼을 추가**하고 `AttractionSyncService`가 매 동기화 때 이미 알고 있는
  값을 그대로 적어두게 했다(추가 API 호출 없음, write-once). 이 값이 아직 없는 행(과거에 동기화된
  뒤 재동기화가 안 지나간 행)은 `overview`/`images`만 받고 `extraInfo`는 다음 동기화 이후로 미룬다.
  병원은 의료관광 상세 API 자체가 contentTypeId 구분이 없어서 애초에 문제 없었고, 숙소는
  `contentTypeId=32` 고정이라 문제 없었다.

## 1. 새 테이블: `place_details`

`place_translations`와 같은 패턴 — `place_type`+`place_id`로 `hospitals_accommodations`/
`attractions`를 논리적으로 참조(다형적이라 DB FK 불가, `place_type`으로 구분). `ddl-auto=update`라
마이그레이션 파일 없이 `@Entity`만 추가하면 스키마가 생긴다(`application-dev.properties`/
`application-prod.properties` 확인 완료).

```
place_details(
  id                BIGINT PK,
  place_type        VARCHAR(16)  NOT NULL,   -- HOSPITAL/ACCOMMODATION/ATTRACTION
  place_id          BIGINT       NOT NULL,   -- hospitals_accommodations.id 또는 attractions.id
  overview          TEXT,                    -- 소개글. 병원=insttDevInfo(기관소개), 숙소/관광명소=detailCommon2.overview
  images            VARCHAR(2048),            -- '|' 구분 URL 목록 (skin_treatment_signals와 같은 컨벤션)
  extra_info        TEXT,                    -- JSON 문자열. 타입별 운영정보 (아래 2절 참고)
  source            VARCHAR(32)  NOT NULL,   -- MEDICALTOURISM(병원) / TOURAPI(숙소·관광명소)
  overridden        BOOLEAN      NOT NULL DEFAULT FALSE,  -- 관리자 수동 수정 시 true, 이후 자동 백필이 안 건드림
  fetched_at        TIMESTAMP,
  UNIQUE(place_type, place_id)
)
```

`domain/PlaceDetail.java` — `PlaceTranslation.java`를 그대로 참고해서 작성:
- write-once 가드: `overridden`이거나 이미 `overview`가 채워져 있으면 `applyDetail(...)`이 아무것도
  안 함(재동기화/재백필 때 덮어쓰지 않음 — `AttractionSyncService`의 popularity와 같은 이유:
  이 정보는 자주 안 바뀌고, 매번 다시 부르면 API 호출량만 낭비).
- `applyDetail(String overview, String images, String extraInfoJson, String source, Instant fetchedAt)`
  하나로 한 번에 씀(번역처럼 필드별로 나눠 채울 필요 없음 — 한 번의 API 응답에서 다 나오므로).

## 2. 타입별 매핑

### 병원(HOSPITAL) — 이미 있는 `MedicalTourismService.getHospitalDetail(contentId, null)` 재사용

| `place_details` 필드 | 출처 (`MedicalTourismDetail`) |
|---|---|
| `overview` | `insttDevInfo` (기관소개) |
| `images` | 없음 → null |
| `extra_info` (JSON) | `{ mainSubject: mainMdlcSubjInfo, specialProcedure: specProcMdlcInfo, serviceLanguage: svcLangInfo, homepage: hmpgInfo, sns: prSnsInfo, history: histrCn, onlineReservation: onlineRsvtPsblYn, consultation: gdsCnselCn, specialFacility: specFcltyInfo, cooperativeHospital: corprHsptlInfo, treatmentGoodsKind: trtmntGdsKndInfo }` |
| `source` | `"MEDICALTOURISM"` |

### 숙소(ACCOMMODATION) — 신규, `contentTypeId=32` 고정이라 모호함 없음

`TourApiClient`에 `fetchDetailCommon2(contentId, contentTypeId)`/`fetchDetailIntro2(...)`/
`fetchDetailImage2(contentId)` 추가(기존 `fetchAreaBasedList`와 같은 방식으로 KorService2 엔드포인트
호출).

| `place_details` 필드 | 출처 |
|---|---|
| `overview` | `detailCommon2.overview` |
| `images` | `detailImage2` 응답 목록을 `\|`로 join |
| `extra_info` (JSON) | `{ checkinTime, checkoutTime, roomCount, subFacility, parking, cooking, pickup, reservationUrl, scale }` (`detailIntro2`, contentTypeId=32 필드셋) |
| `source` | `"TOURAPI"` |

### 관광명소(ATTRACTION) — 신규, contentTypeId를 아는 행만 `extra_info`까지

| `place_details` 필드 | 출처 |
|---|---|
| `overview` | `detailCommon2.overview` |
| `images` | `detailImage2` 응답 목록을 `\|`로 join |
| `content_type_id` | `AttractionSyncService`가 동기화 중 기록(`CategoryConfig.contentTypeId()`, 12/14/38) |
| `extra_info` (JSON) | `content_type_id`가 있을 때만 `detailIntro2`로 채움, 없으면 null. 12(관광지)=`{ useTime, restDate, parking, babyCarriage, pet, expGuide, infoCenter }` / 14(문화시설)=`{ useFee, useTime, restDate, spendTime, discountInfo, parking, infoCenter }` / 38(쇼핑)=`{ openTime, restDate, saleItem, parking, infoCenter }` |
| `source` | `"TOURAPI"` |

## 3. 수집 방식 — 별도 백필 스케줄러 (기존 3개 동기화 서비스는 안 건드림)

`HospitalSyncService`/`AccommodationSyncService`/`AttractionSyncService` 안에 훅을 넣지 않는다 —
동기화 루프마다 신규 행 하나하나에 대해 순차 HTTP 호출이 끼어들면 동기화 자체가 느려진다. 대신
`PlaceTranslationBackfillScheduler`/`PlaceTranslationBackfillService`와 완전히 같은 구조로 별도
배치를 하나 더 만든다.

- `service/PlaceDetailBackfillService.java`:
  1. `hospitals_accommodations`에서 `placeType=HOSPITAL`, `tourismContentId IS NOT NULL`인 행 중
     `place_details`에 아직 행이 없는 것만 골라 의료관광 상세 API 호출 → 저장
  2. 같은 테이블에서 `placeType=ACCOMMODATION`, `tourismContentId IS NOT NULL`인 행 → TourAPI 상세
     (contentTypeId=32) → 저장
  3. `attractions`에서 `tourismContentId IS NOT NULL`인 행 → TourAPI 상세(`detailCommon2`/
     `detailImage2`만) → 저장
  4. `place_translations` 백필과 마찬가지로, 대상 후보를 배치 조회한 뒤 이미 있는 `place_details`
     행 집합을 한 번에 조회해서 걸러낸다(건별 exists 체크로 N+1 만들지 말 것).
- `scheduler/PlaceDetailBackfillScheduler.java`: 매일 새벽 실행. 3개 동기화(04:00/04:30/04:45)가
  끝난 뒤, 번역 백필(05:30)보다 먼저인 **05:00** 정도로 배치.
- `PlaceController`에 `POST /api/places/backfill-details` 수동 트리거 추가(`backfill-translations`와
  동일한 패턴, JWT 필요).

## 4. 응답에 얹기 — `GET /api/places/{id}`만

`PlaceResponse`(`web/dto/PlaceResponse.java`)에 필드 3개 추가:

```java
String overview,
List<String> images,      // DB의 '|' 구분 문자열을 split해서 응답 시점에 List로
Map<String, String> extraInfo   // DB의 JSON 문자열을 응답 시점에 파싱
```

- `PlaceResponse.from(...)`(병원/숙소/관광명소 공용, 목록 API가 쓰는 경로)는 이 세 필드를 항상
  `null`로 채운다 — 목록 API는 지금처럼 가볍게 유지.
- `PlaceResponse.withDetail(PlaceResponse base, PlaceDetail detail)` 정적 메서드를 `withLocaleOverride`
  와 같은 모양으로 추가 — `detail`이 null이면 base 그대로 반환.
- `service/PlaceService.java`의 `getOne(...)`에서만 `placeDetailService.findByPlaceTypeAndPlaceId(placeType, id)`를
  조회해서 `PlaceResponse.withDetail(...)`로 얹는다. `listAll`/`listByType`은 그대로 둔다(수정 없음).

## 5. 새로 만들 파일 목록

1. `domain/PlaceDetail.java`
2. `repository/PlaceDetailRepository.java` — `findByPlaceTypeAndPlaceId`, 백필용 배치 조회 메서드
3. `service/PlaceDetailService.java` — read/write primitive (`PlaceTranslationService` 참고)
4. `tourapi/TourApiClient.java` — `fetchDetailCommon2`/`fetchDetailIntro2`/`fetchDetailImage2` 메서드 추가 (기존 파일 수정)
5. `service/TourApiDetailService.java` — 위 클라이언트 호출 + JSON 매핑 (신규)
6. `service/PlaceDetailBackfillService.java` — 3절의 백필 로직 (신규)
7. `scheduler/PlaceDetailBackfillScheduler.java` (신규)
8. `web/dto/PlaceResponse.java` — 필드 3개 + `withDetail` 추가 (기존 파일 수정)
9. `service/PlaceService.java` — `getOne`에서 `withDetail` 적용 (기존 파일 수정)
10. `web/PlaceController.java` — `POST /api/places/backfill-details` 추가, `getPlace` Swagger 설명 갱신 (기존 파일 수정)

## 6. 실행 순서 (에이전트용)

1. `PlaceDetail` 엔티티 + `PlaceDetailRepository` + `PlaceDetailService` (+ 단위 테스트: write-once 가드 검증)
2. `TourApiClient`에 상세 조회 메서드 추가 + `TourApiDetailService`(응답 파싱/매핑) (+ 단위 테스트)
3. `PlaceDetailBackfillService` — 병원(의료관광 API 재사용)/숙소/관광명소 세 갈래 구현 (+ 배치 조회가 N+1 안 만드는지 확인)
4. `PlaceDetailBackfillScheduler` 등록 + `PlaceController`에 수동 트리거 엔드포인트 추가
5. `PlaceResponse`에 `overview`/`images`/`extraInfo` 필드 + `withDetail` 추가, `PlaceService.getOne`에 연결
6. 통합 테스트: `place_details`에 값이 있는 행 → `GET /api/places/{id}`에 3개 필드가 채워져서 옴 /
   값이 없는 행(예: 카카오 단독 소스) → 3개 필드가 null로 옴 / 목록 API는 이 필드들을 아예 응답에
   안 넣거나(또는 항상 null) 그대로인지 확인
7. `docs/place-detail-page-guide.md` 갱신 — "더 상세한 내용" 섹션을 "이제 `GET /api/places/{id}`
   응답에 `overview`/`images`/`extra_info`가 포함됨"으로 업데이트, 관광명소는 `extra_info`가 항상
   null이라는 제약 명시
8. 로컬 실행 후 수동 확인: `POST /api/places/backfill-details` 호출 → `tourism_content_id` 있는
   병원/숙소/관광명소 각 1건씩 `GET /api/places/{id}`로 조회해서 필드가 채워지는지 눈으로 확인

## 7. 다음 단계로 미룬 것 (이번 범위 아님)

- ~~관광명소 `extra_info`(이용시간/휴무일 등)~~ → **2026-09-06 phase 2로 구현 완료.** 휴리스틱
  역매핑 대신, `AttractionSyncService`가 동기화 중 이미 알고 있는 `contentTypeId`(12/14/38,
  `CategoryConfig`)를 그 자리에서 `place_details.content_type_id`에 적어두는 방식으로 풀었다 —
  추가 API 호출 없음, `attractions` 테이블도 안 건드림(원래 원칙 유지). write-once라 과거에 이미
  동기화된 행은 다음 재동기화 사이클을 한 번 지나야 채워진다. 상세 필드 매핑은
  `TourApiDetailService.fetchAttractionIntro()`와 `place-detail-page-guide.md` 7절 참고.
- `overview`/`extra_info`의 ja/en 번역 — `place_translations`처럼 로케일별로 분리할지는 실제
  수요 보고 판단.
- 관리자 페이지에서 `place_details` 수동 수정(override) UI — 엔티티엔 `overridden` 필드를 이미
  마련해뒀지만, 이번 범위엔 관리 API(`PUT`)까지는 포함하지 않음.
