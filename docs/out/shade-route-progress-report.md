# 강남구 그늘길 라우팅 프로토타입 — 진행 리포트

`docs/shade-route.md` 스펙을 기준으로 지금까지 구현·검증한 내용을 정리한다.
작성일: 2026-08-06

## 요약

배치(Python)에서 강남구 건물+그늘+보행망을 전부 오프라인으로 미리 계산해
`data/out/`에 굽고, 그 결과를 리포 루트의 Afterglow Spring Boot 앱에
`com.afterglow.shaderoute` 패키지로 흡수해 그늘 가중 Dijkstra API를 붙였다.
프론트는 빌드 도구 없는 단일 HTML로 만들어 같은 앱의 `static/`에서 서빙한다.
DB/JPA/PostGIS는 shade-route 기능 어디에도 쓰지 않았다(문서 원칙 준수).

```
data/raw (shp) → batch/src/*.py → data/out/*.geojson, shade_table.bin
                                         │
                                         ▼
                     src/main/java/com/afterglow/shaderoute (Spring Boot, 부팅 시 힙 로드)
                                         │
                                         ▼
                     src/main/resources/static/index.html (MapLibre + turf + suncalc)
```

## 1. 배치 파이프라인 (`batch/src/`)

### 1-1. `buildings.py` — 건물 footprint + 높이

- `data/raw/AL_D010_11_20260719.shp`(서울 전체, 695,769건)를 실제로 읽어 컬럼을 확인.
  문서에 적혀 있던 `bld_seoul.shp` / 명명된 컬럼과 달리 실제 컬럼명은 `A0~A28`로
  익명화돼 있고 dbf 인코딩은 `cp949`였다(추측하지 않고 직접 읽어서 확인).
- 매핑 확정: `BLD_ID=A0`, `ADDR=A4`, `USE_NAME=A9`, `HEIGHT_RAW=A16`,
  `SIG_CD=A23`, `GRND_FLR=A26`.
- 강남구 필터: `SIG_CD == '11680'` → **28,228건**.
- 높이 보정: `HEIGHT_RAW`가 결측이거나 2~300m 밖(0 포함)이면
  `GRND_FLR * 3.3`으로 폴백.
  - 보정 전: 결측 7건, 이상치 11,629건
  - 보정 후: 결측 7건(그대로 — GRND_FLR도 없음), 이상치 5,971건(GRND_FLR=0이라
    폴백해도 여전히 2m 미만인 케이스) — 임의로 감추지 않고 그대로 보고.
- 산출물: `batch/debug/height_dist.png`(보정 전/후 히스토그램).
- 전체 695,769건을 메모리에 올리지 않고 OGR `WHERE A23='11680'` 절로
  강남구만 읽어 로딩 시간을 줄임(pyogrio 직접 호출, ~58초).

### 1-2. `check_height_correction.py` — 보정 로직 QA

- "HEIGHT_RAW가 정상범위(2~300)인데 보정 후 값이 바뀐 건물"을 찾는 스크립트.
- 결과: **0건** — 정상범위 값을 건드리지 않는다는 것을 실제 데이터로 확인.
- 산출물: `batch/debug/height_correction_check.csv`.

### 1-3. `shadow.py` — 그림자 폴리곤 계산

- `compute_shadows(buildings, when)`: `pvlib.solarposition.get_solarposition`으로
  기준점(37.5172, 127.0473)의 태양 고도/방위 계산 → `apparent_elevation`(대기굴절
  보정치) 사용, 고도각 ≤3도면 빈 결과.
- `L = h / tan(고도각)`, 그림자 방향 = 태양 방위각 + 180도로 footprint를
  translate → 원본과 union → convex_hull.
- `geopandas.GeoSeries.translate()`가 건물마다 다른 offset(배열)을 지원하지
  않아 `shapely.affinity.translate`를 건물별로 적용하는 방식으로 구현
  (직접 테스트로 확인 후 결정).
- 검증: 삼성동 2,829건, 2026-08-15 09:00/12:00/17:00 세 시각 — 09:00은
  그림자가 서쪽, 17:00은 동쪽으로 정반대 방향임을 확인.
- 산출물: `batch/debug/shadow_check.png`.

### 1-4. `graph.py` + `main.py` — 보행망 + shade_table 생성

- `graph.py`: `osmnx.graph_from_place("Gangnam-gu, Seoul, South Korea", network_type="walk")`
  → EPSG:5186 투영 → 엣지 26,010개(방향 그래프, 양방향 도로는 두 엣지),
  노드 8,790개. 엣지를 8m 간격으로 샘플링(`sample_edges`) → 샘플 포인트
  225,792개.
- `main.py`: 05:00~20:00 15분 버킷(61개) 전체에 대해
  - 버킷마다 `shadow.compute_shadows()`로 그림자 계산
  - **`shapely.strtree.STRtree`로 그림자 폴리곤 인덱스**를 만들고, 샘플 포인트
    225,792개를 `tree.query(points, predicate="intersects")` 한 번의 벡터 호출로
    질의(포인트×건물 전수비교를 피하는 지점 — 요청하신 공간 인덱스 요건).
  - 엣지별 `그늘 포인트 수 / 전체 포인트 수` 비율을 `uint8`(0~255)로 기록.
  - 버킷마다 `[i/61] HH:MM 완료 (Xs)` 진행 로그 출력.
- 전체 실행 결과(재계산 완료본 기준): **272.4초**, 05:00~06:00·19:15~20:00은
  고도각 3도 이하라 0.0초(스킵), 나머지는 버킷당 3~7초.
- 산출물(`data/out/`):
  - `shade_table.bin` — uint8, row-major `[bucket][edge_id]`, **1,586,610 bytes**
    (61 × 26,010)
  - `shade_meta.json` — n_buckets/n_edges/bucket_start(05:00)/bucket_minutes(15)/
    representative_date(2026-08-15)/timezone(Asia/Seoul)
  - `edges.geojson` — 26,010 feature, EPSG:4326, `edge_id/u/v/length`
  - `buildings.geojson` — 28,228 feature, EPSG:4326, `BLD_ID/ADDR/USE_NAME/HEIGHT`
- 대표 엣지 선정 로직에서 시행착오: 가장 긴 엣지를 고르면 공원 산책로처럼
  건물이 없어 그늘이 하루 종일 0, 총 그늘량 최대로 고르면 반대로 고층건물
  사이 통로처럼 하루 종일 100%인 평평한 곡선이 나옴 → 최종적으로
  "길이 40~200m(샘플 포인트 여러 개 확보) 범위 안에서 그늘 비율 분산이 가장
  큰 엣지"로 정착. 산출물: `batch/debug/shade_curve.png`
  (남북 edge_id=26003, 동서 edge_id=17009).

### 1-5. `edge_shadow_map.py` — 엣지 주변 그림자 육안 검증

- edge_id=26003(남북)/17009(동서) 각각 중심 반경 200m를
  05:00/06:00/12:00/17:00 네 시각(2×4=8분할)으로 렌더링.
- 두 엣지가 실제로는 5.2km 떨어져 있어(강남구 내 다른 동) 8분할 그리드로
  나눔. 05:00은 그림자 없음, 시간이 지날수록 그림자가 길어지고 방향이
  이어지는 걸 확인.
- 산출물: `batch/debug/edge_shadow_map.png`.

## 2. 서버 (`src/main/java/com/afterglow/shaderoute/`)

처음엔 `server/`라는 완전히 독립된 Gradle 프로젝트로 만들었다가(루트 앱이
JPA/Security/OAuth2를 쓰는 실제 운영 백엔드라 분리했었음), 이후 사용자
요청으로 **리포 루트의 기존 Afterglow 앱에 최종 흡수**했다.

- `graph/ShadeGraph.java` (`@Component`, `@PostConstruct`): `shade.data-dir`
  (`application.properties`의 `data/out`)에서 4개 파일을 부팅 시 힙에 로드.
  - `edges.geojson`은 Jackson `JsonNode` 트리로 파싱해 `u/v/length`와
    좌표(엣지 LineString의 첫/끝 좌표 = 노드 좌표, 별도 nodes 파일 없음)를
    추출.
  - `shade_table.bin`은 raw `byte[]` 그대로 로드. uint8이므로 조회 시
    `& 0xFF`로 부호 없는 값으로 변환(`shadeRatio()`).
  - `buildings.geojson`은 **파싱하지 않고 원본 바이트 그대로** 캐시해서
    서빙(초기엔 JsonNode로 파싱했다가 요청마다 재직렬화하느라 16초 넘게
    걸리는 문제를 발견해 수정함).
  - `nearestNode(lat, lon)`: 노드 8,790개 선형 스캔 + Haversine — 이 규모에선
    공간 인덱스 없이도 충분히 빠름(배치의 STRtree는 포인트 22만 개
    규모라 필요했던 것과 다른 상황).
  - `bucketIndexFor(LocalTime)`: 05:00~20:00 밖이면 경계 버킷으로 클램프.
- `route/RouteService.java`: 그늘 가중 Dijkstra.
  `cost(edge) = length + lambda * length * (1 - shadeRatio)`.
  `PriorityQueue` 기반, 경로 복원 시 엣지 좌표를 이어붙여 LineString 생성,
  거리(m)와 length-가중 평균 그늘 비율을 같이 반환.
- `route/RouteController.java`: `GET /api/route?from=lat,lon&to=lat,lon&at=ISO8601`
  → `lambda=0.0`("shortest")과 `lambda=0.8`("shady") 두 결과를 한 번에 반환.
  출발/도착은 `nearestNode`로 스냅.
- `route/BuildingsController.java`: `GET /api/buildings` — `buildings.geojson`
  원본을 그대로 passthrough.

### 검증 결과 (실제 좌표로 호출)

```
GET /api/route?from=37.498,127.028&to=37.491,127.086&at=2026-08-15T14:30:00+09:00
  shortest(λ=0.0): 6683.4m, 평균 그늘 11.1%
  shady(λ=0.8):    6915.5m(+3.5%), 평균 그늘 32.4%
05:00(그늘 없는 버킷) 호출 시 두 경로가 완전히 동일 → cost가 length로 수렴함을 확인
```

## 3. 프론트 (`src/main/resources/static/index.html`)

빌드 도구 없이 CDN(MapLibre GL 4.7.1, turf 6, suncalc 1.9.0)만 사용하는
단일 HTML.

- 배경 지도는 Protomaps 대신 API 키가 필요 없는 **OpenStreetMap 무료 래스터
  타일**로 대체.
- `/api/buildings`를 최초 1회만 fetch, 이후 시간 슬라이더 조작 시 서버 호출
  없이 브라우저에서 `suncalc`(태양 위치) + `turf`(translate/convex hull)로
  그림자를 즉시 재계산 — `shadow.py`와 동일한 알고리즘(단, union은 생략:
  두 폴리곤의 convex hull은 union한 뒤 hull을 구한 것과 결과가 같아서
  단계를 하나 줄임).
- 현재 지도 뷰포트 안 건물만 계산(28,228개 전부를 매 프레임 계산하면 느림).
- 지도 2번 클릭 → 출발(파랑)/도착(빨강) 마커 → `/api/route` 호출 →
  최단 경로(회색)/그늘 경로(초록) + 거리·그늘 비율 % 라벨.
- 실제 브라우저(Chrome, claude-in-chrome 도구)로 직접 열어 렌더링, 슬라이더,
  2클릭 경로 생성까지 전부 확인함.

## 4. 리포 구조 변경 이력

1. `web/index.html` (독립 파일) → `server/src/main/resources/static/index.html`
   (Spring Boot 정적 리소스 규칙에 맞춤, API_BASE를 상대경로로 변경)
2. `server/`(독립 Gradle 프로젝트) 전체 → 리포 루트 앱에 흡수
   - Java 소스: `server/src/main/java/com/afterglow/shaderoute/**` →
     `src/main/java/com/afterglow/shaderoute/**`
   - 중복되던 `ShadeRouteApplication`(main 클래스), `CorsConfig`(루트 앱의
     기존 Security 기반 CORS와 중복)는 흡수하면서 제거
   - `application.properties`에 `shade.data-dir=data/out` 추가
   - **`SecurityConfig.java`에 `/`, `/index.html`, `/api/route`,
     `/api/buildings`를 `permitAll()`에 추가** — 안 하면 기존
     `anyRequest().authenticated()` 규칙에 막혀 그늘길 기능이 전부 401/302.
   - `server/` 디렉터리 삭제, `docs/shade-route.md` 명령어 절 갱신

현재는 `./gradlew bootRun`(포트 8080) 하나로 기존 Afterglow 기능 +
그늘길 API + 프론트가 전부 같이 뜬다.

## 5. 알려진 한계 / 후속 과제

- **대표 날짜 고정**: `shade_table`은 2026-08-15 하루 기준으로 고정 계산됨.
  다른 날짜의 실제 그림자와는 다를 수 있음(여름철 그늘길이라는 프로토타입
  취지엔 부합).
- **`street_trees.csv` 미사용**: `docs/shade-route.md`에 언급된 가로수
  그늘(수관 반경 2.5m 버퍼, 높이 7m 가정)은 아직 구현하지 않음 — 건물
  그림자만 반영됨.
- **그림자 계산이 단일 기준점**: 배치·프론트 모두 강남구 전체에 대해 하나의
  태양 위치(37.5172, 127.0473)만 씀. 건물별 정밀도보단 프로토타입 단순화.
- **경로 시각화**: 최단/그늘 경로가 겹치는 구간에서는 초록선이 위에 그려져
  회색이 안 보일 수 있음(둘 다 정상적으로 그려지고 있고 데이터도 다름 —
  단순히 겹쳐서 안 보이는 것뿐).
- **보안 관련 별도 발견 사항**: `src/main/resources/application-local.properties`에
  Notion API 토큰/Google OAuth 시크릿/공공데이터포털 서비스키가 평문으로
  커밋돼 있음. 이번 작업 범위는 아니라 손대지 않았음 — 환경변수로 옮기는 걸
  권장.

## 6. 산출물 목록

| 경로 | 내용 |
|---|---|
| `batch/debug/height_dist.png` | 높이 보정 전/후 히스토그램 |
| `batch/debug/height_correction_check.csv` | 정상범위 오염 여부 QA (0건) |
| `batch/debug/shadow_check.png` | 삼성동 09/12/17시 그림자 비교 |
| `batch/debug/shade_curve.png` | 남북/동서 대표 엣지 시간별 그늘 비율 곡선 |
| `batch/debug/edge_shadow_map.png` | 대표 엣지 주변 200m, 4시각 그림자 지도 |
| `data/out/shade_table.bin` | uint8 그늘 비율 테이블 (61×26,010) |
| `data/out/shade_meta.json` | shade_table 차원/버킷 메타정보 |
| `data/out/edges.geojson` | 강남구 보행 엣지 (EPSG:4326) |
| `data/out/buildings.geojson` | 강남구 건물 footprint+높이 (EPSG:4326) |
| `src/main/java/com/afterglow/shaderoute/**` | ShadeGraph, RouteService(Dijkstra), 컨트롤러 |
| `src/main/resources/static/index.html` | MapLibre+turf+suncalc 단일 페이지 프론트 |
