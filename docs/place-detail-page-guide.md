# 장소 상세 페이지 가이드 (병원 / 숙소 / 관광명소)

프론트엔드에서 병원·숙소·관광명소 상세 페이지를 만들 때 참고할 API 계약과 화면 구성 권장안.
세 종류 모두 응답 스키마(`PlaceResponse`)는 동일하지만, 타입별로 실제 값이 채워지는 필드가
다르므로 그 차이를 기준으로 화면을 나눠 설계하는 걸 권장한다.

## 1. API

```
GET /api/places/{id}?placeType=HOSPITAL|ACCOMMODATION|ATTRACTION&lang=ja|en
```

- `placeType`은 필수다. 같은 `id`라도 `HOSPITAL`/`ACCOMMODATION`은 `hospitals_accommodations`
  테이블, `ATTRACTION`은 `attractions` 테이블에서 각자 독립적으로 채번되므로 없으면 어느 테이블
  행인지 알 수 없다. 목록 API(`/api/places`, `/api/places/hospital` 등) 응답의 `placeType` 값을
  그대로 상세 페이지 요청에 넘기면 된다.
- `lang`은 생략 가능. `ja`/`en`만 지원하고, 그 외 값이나 생략 시 한국어 원본을 반환한다.
- 인증 불필요(공개 API).

## 2. 응답 필드 사전

⚠️ **응답 JSON은 camelCase다** — Jackson 기본 설정 그대로라 `place_name`이 아니라 `placeName`으로
온다(실제 배포된 API로 직접 확인함, 2026-09-05). 타입별 채움 여부를 꼭 확인하고 화면을 만들어야
한다 — 안 채워지는 필드를 무조건 렌더링하면 병원 상세에 빈 별점이 뜨는 식의 버그가 난다.

| 필드 | 설명 | HOSPITAL | ACCOMMODATION | ATTRACTION |
|---|---|:---:|:---:|:---:|
| `id` | 내부 PK | ● | ● | ● |
| `placeId` | 카카오 place id. 없으면 카카오 미매칭(TourAPI 원본만 있는 행) | 있을 수도/없을 수도 | 있을 수도/없을 수도 | 있을 수도/없을 수도 |
| `tourismContentId` | 관광공사/의료관광 API contentId | 있을 수도/없을 수도 | 있을 수도/없을 수도 | 있을 수도/없을 수도 |
| `placeName` | 장소명 | ● | ● | ● |
| `categoryName` | 카테고리 계층 텍스트(">" 구분) | ● | ● | ● |
| `addressName` | 지번 주소. **로케일 무관 항상 한국어** | ● | ● | ● |
| `mapX` / `mapY` | 경도/위도 | ● | ● | ● |
| `image` | 대표 이미지 URL | 없을 수도 (TourAPI 상당수가 이미지 없음) | 없을 수도 | 없을 수도 |
| `phone` | 전화번호 | 없을 수도 | 없을 수도 | 없을 수도 |
| `placeUrl` | 카카오맵 상세 링크 | **카카오 미매칭이면 null** | **카카오 미매칭이면 null** | **카카오 미매칭이면 null** |
| `source` | 데이터 출처(디버깅용, 사용자 노출 X) | ● | ● | ● |
| `primaryTypeName` | 표시용 유형명 | 항상 `"병원"` | — | 카페/미술관/공연장 등 분류 결과 |
| `skinTreatmentConfidence` | 피부시술 신뢰도(`confirmed`/`high`/`medium`) | 있을 수도 (병원 전용) | 항상 null | 항상 null |
| `skinTreatmentSignals` | 발견 키워드, `\|` 구분 | 있을 수도 (병원 전용) | 항상 null | 항상 null |
| `isIndoor` | 실내 여부 | 항상 null | 항상 null | 있을 수도 (관광명소 전용) |
| `isHeatSource` | 폭염 시 피해야 할 발열원 여부 | 항상 null | 항상 null | 있을 수도 |
| `isMassageSpot` | 시술 후 방문 적합 여부 | 항상 null | 항상 null | 있을 수도 |
| `walkHard` | 도보 난이도(낮을수록 쉬움) | 항상 null | 항상 null | 있을 수도 |
| `popularity` | 0~5 인기도(카카오 평점 70% + 블로그 리뷰량 30%) | 항상 null | 항상 null | 있을 수도(카페/미술관/공연장/찜질방·사우나/안마·스파만 계산됨, 그 외 유형은 항상 null) |

## 3. 공통 레이아웃 (세 타입 공통)

1. **헤더**: `image`(없으면 플레이스홀더) + `placeName` + `categoryName`
2. **위치**: `addressName` 텍스트 + `mapX`/`mapY`로 지도 임베드(카카오맵 SDK 등)
3. **연락**: `phone` (없으면 항목 자체를 숨김)
4. **CTA**: `placeUrl`이 있을 때만 "카카오맵에서 보기" 버튼 노출. `placeUrl`이 null이면 버튼을
   숨기거나, `placeName` + `addressName`으로 카카오맵 검색 URL(`https://map.kakao.com/?q=...`)을
   조립한 대체 링크로 바꾸되 문구는 "카카오맵에서 검색"처럼 정확한 딥링크가 아님을 구분해서 표기.
5. `source`, `syncedAt`, `tourismContentId`, `imageOverridden` 등은 관리자/디버깅용 필드라
   일반 사용자 상세 페이지엔 노출하지 않는 걸 권장.

## 4. 병원(HOSPITAL) 전용

공통 레이아웃에 아래를 추가:

- **시술 신뢰도 뱃지**: `skinTreatmentConfidence`
  - `confirmed` → 강조 뱃지(예: 초록) — 카카오 카테고리 자체가 피부과
  - `high` → 파랑 계열 — 시술 키워드 2개 이상 매칭
  - `medium` → 회색/보조 톤 — 키워드 1개만 매칭, **오탐 가능성 있음**(실제로 해당 시술을
    제공하는지 확정된 정보가 아니라는 문구를 같이 넣는 걸 권장)
  - 값 자체가 없으면(null) 뱃지를 아예 숨김 — 이 병원은 시술 키워드 스윕에서 발견되지 않았다는
    뜻이지 "시술 안 함"이 아니므로 "정보 없음" 같은 부정적 문구는 쓰지 말 것
- **시술 태그**: `skinTreatmentSignals`를 `|`로 split해서 칩 목록으로 표시(예: "리프팅",
  "보톡스"). `kakao_category:피부과`처럼 접두어가 붙은 항목은 UI에서 접두어를 떼고 "피부과"로만
  표시하거나 별도 스타일로 구분.

## 5. 숙소(ACCOMMODATION) 전용

병원의 시술 정보, 관광명소의 도보/인기도 정보가 전부 null이라 특화 필드가 없다. 공통 레이아웃만
적용하면 되고, CTA는 "예약"이 아니라 "카카오맵에서 위치 확인" 정도로 문구를 잡는 게 정확하다
(백엔드가 실제 예약 연동을 제공하지 않음).

## 6. 관광명소(ATTRACTION) 전용

공통 레이아웃에 아래를 추가:

- **유형 뱃지**: `primaryTypeName`(카페/미술관/공연장/박물관/영화관/찜질방·사우나/안마·스파/공원 등)
- **인기도**: `popularity`(0~5)를 별점 UI로. **일부 유형만 계산됨**(카페/미술관/공연장/찜질방·사우나/
  안마·스파) — 그 외 유형은 항상 null이므로 별점 영역 자체를 조건부 렌더링. null을 "0점"으로
  오인해서 빈 별 5개를 그리지 않도록 주의.
- **도보 난이도**: `walkHard`(낮을수록 쉬움) — 숫자를 그대로 노출하기보다 "쉬움/보통/힘듦" 같은
  3~4단계 라벨로 매핑해서 보여주는 걸 권장(정확한 구간 기준은 백엔드에 문의).
- **컨디션 뱃지**: `isIndoor`(실내/실외), `isHeatSource`(폭염 시 주의), `isMassageSpot`(시술
  후 방문 추천)를 아이콘/뱃지로. 세 값 모두 극히 일부 예외 행에서 null일 수 있으니 null이면 뱃지를
  숨기고, false는 명시적으로 "아니오" 뱃지를 그릴지 아예 숨길지는 디자인 톤에 맞춰 결정.

## 7. 더 상세한 내용(개요/이미지/운영정보) — `overview`/`images`/`extraInfo`

`docs/place-detail-info-plan.md`에서 설계한 대로 구현·배포 완료됨(`place_details` 테이블 +
`PlaceDetailBackfillScheduler` 매일 새벽 5시, 2026-09-05 프로덕션 확인). **`GET /api/places/{id}`
에서만** 아래 3개 필드가 추가로 온다 — 목록 API(`/api/places`, `/api/places/hospital` 등)엔
안 나온다(payload를 가볍게 유지하려고 일부러 뺌).

```json
{
  "...": "기존 PlaceResponse 필드",
  "overview": "서울 강남 한복판에 위치한 프리미엄 스킨케어 클리닉으로...",
  "images": ["https://tong.visitkorea.or.kr/cms/resource/.../a.jpg", "..."],
  "extraInfo": {
    "mainSubject": "피부과, 성형외과",
    "serviceLanguage": "영어, 일본어, 중국어",
    "onlineReservation": "Y"
  }
}
```

- 셋 다 **`tourismContentId`가 있는 행에만** 채워질 수 있다. `source=KAKAO` 단독 행(카카오
  시술/AD5/CE7 스윕으로만 생성된 행)은 애초에 대상이 아니라서 항상 `null`이다 — "정보 없음"이지
  에러가 아니다. 백필 스케줄러가 아직 안 돌았거나 외부 API 호출이 실패한 경우도 마찬가지로 `null`.
- `extraInfo`의 키는 타입마다 다르다:
  - **병원**: `mainSubject`(주요 진료과목), `specialProcedure`, `serviceLanguage`, `homepage`,
    `sns`, `history`, `onlineReservation`, `consultation`, `specialFacility`,
    `cooperativeHospital`, `treatmentGoodsKind`
  - **숙소**: `checkinTime`, `checkoutTime`, `roomCount`, `subFacility`, `parking`, `cooking`,
    `pickup`, `reservationUrl`, `scale`
  - **관광명소**: `extraInfo`가 **항상 `null`**이다 — `overview`/`images`만 온다. TourAPI 상세
    조회(`detailIntro2`)가 `contentTypeId`(12/관광지·14/문화시설·38/쇼핑)를 요구하는데
    `attractions` 테이블엔 이 값이 저장돼 있지 않아서 안전하게 못 부른다(`place-detail-info-plan.md`
    0절 참고). `extraInfo` 자체가 없으니 프론트에서 이 키들을 참조하지 말고, 관광명소는 6절의
    `primaryTypeName`/`walkHard`/`popularity` 등 기존 필드로만 운영정보를 대체 표현할 것.
  - 응답에 없는 키는 원본 API에도 값이 없었다는 뜻(빈 문자열이 아니라 키 자체가 생략됨) — 프론트는
    각 키를 옵셔널로 취급.

**실측 현황(2026-09-05, 첫 백필 실행)**: 병원 162건 채움(관광공사 데이터가 이미 사라진 2건만
정상 스킵). 숙소/관광명소는 **data.go.kr TourAPI 서비스키의 일일 호출 한도(`data.go.kr` 자체
제한, `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR`)에 걸려 그날은 0건** — 코드 버그가
아니라 외부 API 쿼터 문제였고, 매일 새벽 5시 스케줄러가 매일 조금씩 이어서 채운다(한도 초과
감지되면 그 타입은 바로 중단하고 다음날 이어감, 무의미한 반복 호출 안 함). 프론트는 그래서
숙소/관광명소는 **당분간 상당수 행이 `overview`/`images`가 null인 상태로 보일 수 있다**는 점을
감안해서 UI를 만들어야 한다(9절 체크리스트 참고).

## 8. 다국어(`lang=ja`/`en`) 처리 시 주의

- `placeName`/`categoryName`만 번역 대상이고, 번역이 아직 없는 행은 한국어 원본으로 폴백된다
  (필드 자체는 항상 채워져 있으니 프론트에서 null 처리할 필요는 없음).
- `addressName`은 로케일과 무관하게 **항상 한국어 원문**이다. 일본어/영어 사용자에게는 주소
  영역에 "주소는 한국어 표기 그대로 제공됩니다" 같은 짧은 안내를 붙이는 걸 권장(택시 기사·현지
  지도 앱에 그대로 보여줘야 하는 값이라 번역하지 않는 것).

## 9. 엣지 케이스 체크리스트

- [ ] `image` null → 플레이스홀더 이미지
- [ ] `phone` null → 연락처 영역 숨김
- [ ] `placeUrl` null → CTA 버튼 숨김 또는 검색 링크로 대체
- [ ] 병원 `skinTreatmentConfidence` null → 뱃지 숨김(부정 문구 금지)
- [ ] `overview`/`images`/`extraInfo` null → 상세 소개 섹션 통째로 숨김("정보 없음" 문구도 굳이
      안 넣는 게 낫다 — 카카오 단독 소스 행이면 앞으로도 절대 안 채워지고, 숙소/관광명소는 TourAPI
      일일 쿼터 때문에 당장은 null인 행이 많다 — 7절 참고)
- [ ] 관광명소는 `extraInfo`가 항상 null → 이 필드의 키들(체크인/체크아웃 등)을 참조하는 코드
      자체를 관광명소 상세 화면엔 넣지 말 것
- [ ] 관광명소 `popularity`/`isIndoor` 등 null → 해당 UI 블록 통째로 숨김(0/false로 오인 렌더링 금지)
- [ ] `lang=ja`/`en`인데 번역 미존재 → 한국어 원본이 그대로 오므로 별도 처리 불필요, 다만 주소는
      항상 한국어라는 안내 문구 필요
