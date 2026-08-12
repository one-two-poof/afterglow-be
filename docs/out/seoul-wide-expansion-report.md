# 그늘길 서울 전체 확장 — 진행 리포트

`docs/out/shade-route-progress-report.md`(강남구 프로토타입) 이후 작업을 정리한다.
작성일: 2026-08-12

## 요약

세 가지 작업을 순서대로 진행했다.

1. **프론트를 PMTiles 방식으로 전환** — `buildings.geojson` 통짜 fetch를 pmtiles
   벡터 타일로 교체 (서울 전체로 커지는 걸 감안한 선행 작업).
2. **배치 파이프라인을 강남구 전용 → 서울 전체로 확장** — `graph.py`/`main.py`를
   pyrosm 기반 OSM 추출 + 자치구 25개 병렬 그림자 계산으로 재작성.
3. **서버 측 성능/정확성 보강** — `ShadeGraph.nearestNode()`를 KD-tree로 교체하고
   스냅 거리 검증을 추가, 이 과정에서 발견한 라우팅 오류(먼 지점이 조용히
   엉뚱한 곳으로 스냅되던 문제)를 해결.

세 작업 모두 실행 도중 예상 밖의 문제(pyrosm 상호운용성 버그 2건, 메모리 부족,
위상 오류 건물 데이터, WSL 세션 불안정, Jackson 트리 파싱 OOM)를 만나 그때그때
원인을 찾아 고쳤다. 아래에 실측 수치와 함께 정리한다.

```
data/raw/*.shp ──┐
                 ├─▶ batch/src/buildings.py (서울 전체 695,769건)
Geofabrik pbf ───┤
  (WSL+pyrosm)   └─▶ batch/src/wsl_extract_network.py → batch/cache/*.gpkg
                          │
                          ▼
                 batch/src/graph.py (읽기만) + district.py (25구 병렬) + main.py
                          │
                          ▼
                 data/out/{edges,buildings}.geojson, nodes.bin, shade_table_*.bin
                          │
                          ▼
        src/main/java/com/afterglow/shaderoute (ShadeGraph — 스트리밍 파싱, KD-tree)
                          │
                          ▼
        src/main/resources/static/index.html (MapLibre + pmtiles + turf + suncalc)
```

## 1. 프론트: PMTiles 마이그레이션

- `pmtiles` JS 라이브러리(CDN)를 추가하고 `maplibregl.addProtocol`로 등록.
- `buildings` 소스를 `type: 'geojson'` 통짜 fetch에서 `type: 'vector'`
  pmtiles 소스(`pmtiles://data/buildings.pmtiles`)로 교체.
- `updateShadows()`가 메모리에 들고 있던 `buildingsData` 전체를 순회하던 방식에서
  `map.querySourceFeatures('buildings')`로 현재 로드된 타일의 피처만 쓰도록 변경.
  벡터 타일이 경계에서 폴리곤을 조각내 인접 타일에 중복 등장시킬 수 있어
  `BLD_ID` 기준 중복 제거를 추가.
- `sourcedata` 이벤트에도 `updateShadows`를 걸어, 타일이 비동기로 늦게 들어와도
  그늘이 갱신되게 함.
- `BuildingsController.java`/`ShadeGraph`의 `buildingsGeoJsonBytes` 캐싱 로직 제거
  (더 이상 서버가 buildings.geojson을 통째로 서빙하지 않음).
- `SecurityConfig.java`: `/api/buildings` permitAll 제거, 정적 리소스
  `/data/**`(pmtiles 파일) permitAll 추가.
- `batch/src/make_tiles.sh` 신설 — WSL의 tippecanoe로
  `data/out/buildings.geojson` → `buildings.pmtiles` 변환
  (`-zg --drop-densest-as-needed -l buildings`).
  - tippecanoe는 Windows 네이티브 빌드가 안 돼 WSL Ubuntu에 소스 빌드해 사용.
- 좌표 범위 검증 추가(`buildings.py`): centroid가 대한민국 대략 범위
  (경도 124~132, 위도 33~43) 밖이면 `batch/debug/coord_outliers.csv`로 분리하고
  `buildings.geojson`에서 제외. 실제로는 이 범위 기준 0건 — 이전에 발견했던
  "강북구 주소인데 좌표는 부산 인근" 이상치 2건은 국가 범위 안이라 안 걸러짐
  (서울 범위로 좁혀야 잡힘, 필요시 후속 작업).

## 2. 배치 파이프라인 서울 전체 확장

### 2-1. OSM 데이터 소스 교체: Geofabrik pbf + pyrosm

`osmnx.graph_from_place("Seoul...")`는 Overpass에 거대한 그래프 쿼리를 던져
타임아웃/메모리 위험이 크다는 우려로, Geofabrik 한국 추출본(`south-korea-latest.osm.pbf`,
271MB)을 미리 받아 pyrosm으로 서울 경계만 잘라 쓰는 방식으로 바꿨다.

- `fetch_osm_pbf.py`(신설): Geofabrik pbf 1회 다운로드.
- `boundaries.py`(신설): `osmnx.geocode_to_gdf`로 서울 전체 + 자치구 25개 경계
  폴리곤만 가져옴(그래프 전체가 아니라 경계 하나만 요청하는 가벼운 조회).
- **pyrosm은 Windows에서 설치 불가**: 의존 패키지 `cykhash`가 Windows용
  사전 빌드 wheel이 없어 로컬 C++ 컴파일이 필요한데 이 머신엔 컴파일러가
  없음. WSL Ubuntu에 `~/osmenv`라는 별도 venv를 만들어 pyrosm/osmnx/geopandas를
  설치하고, OSM 추출은 WSL 전용 스크립트(`wsl_extract_network.py`)가 전담.
  Windows 쪽 `graph.py`는 pyrosm을 import하지 않고 그 결과(GeoPackage)를
  읽기만 하도록 아키텍처를 분리했다.
- **실측 중 발견한 버그 2건**:
  1. `ox.project_graph()`가 pyrosm이 만든 그래프에서는 `G.graph['crs']`
     메타데이터만 EPSG:5186으로 바꾸고 실제 엣지 좌표는 lon/lat 그대로
     남기는 상호운용성 버그. → `graph_to_gdfs`로 4326 그대로 뽑은 뒤
     `GeoDataFrame.to_crs()`로 직접 재투영하는 방식으로 우회.
  2. `pyrosm.to_graph()`는 `simplified=False`로 나와(osmnx 기본값과 다름)
     교차로가 아닌 중간 노드까지 전부 별도 엣지가 됨 — 강남구 재검증 시
     osmnx 기준 26,010개 대비 약 2배(53,822개). `ox.simplify_graph()`로
     26,362개까지 맞춰지는 것도 확인했지만, 서울 전체 규모에서
     `simplify_graph`가 메모리 부족(SIGKILL)의 원인이 되어 **최종적으로는
     생략**(사용자 승인) — 정확성 문제는 아니고 엣지가 더 잘게 쪼개질 뿐.
- 경계 클리핑은 `gpd.clip()`을 쓰지 않는다 — LineString이 경계에서 잘리면
  `GraphEdge.java`가 의존하는 불변조건(엣지 첫/끝 좌표 = 노드 좌표)이 깨짐.
  `edges.geometry.intersects(boundary_union)`로 겹치는 엣지를 통째로 유지.
- **WSL 세션이 예측 불가능한 시점에 죽는 문제**(OS 레벨 kill, Python 예외
  아님)로 서울 전체를 한 번에 추출하는 시도가 반복 실패. 25개 구를 하나씩
  독립적으로 추출하고 구별로 결과를 즉시 디스크에 저장(`batch/cache/gu_extract/`)해
  이미 끝난 구는 건너뛰는 재개 가능한 구조로 재설계, **23회 재시도** 끝에
  25개 구 전부 추출 완료.
- 최종 결과: `batch/cache/seoul_walk_edges_5186.gpkg` **1,145,124개 엣지**,
  `seoul_walk_nodes_4326.gpkg` **501,728개 노드**.

### 2-2. 계산량 완화

- `SAMPLE_SPACING_M`: 8.0 → 15.0 (`graph.py`)
- `BUCKET_MINUTES`: 15 → 30, 61 → 31 버킷/일 (`shadow.py`로 이동)

### 2-3. 자치구 25개 병렬 처리 (`district.py` 신설)

- **"경계 + 버퍼" 방식**: 엣지 소유권(출력)은 구 경계 기준으로 정확히 나누고,
  그림자 계산 입력(건물)은 자기 구 + 버퍼(1000m) 이내 이웃 구 건물까지
  포함. 구 경계에 딱 맞춰 자르면 옆 구 건물이 이쪽 구 도로에 드리우는
  그림자가 누락되는 문제(높은 건물일수록 그림자가 수백m까지 뻗음)를 방지.
- 엣지→구 배정은 엣지 중점을 25개 구 경계와 공간조인(`gpd.sjoin`)해서 결정,
  경계선상이라 매칭 안 되는 엣지는 `sjoin_nearest`로 최근접 구에 폴백.
- **구별·날짜별 결과 캐싱**(`batch/cache/district_results/`) 추가 — 25구×4일
  전체 실행이 1시간 넘게 걸리는데 배경 프로세스가 예측 불가능하게 죽는 일이
  잦아, 이미 계산된 (구,날짜) 조합은 재실행 시 건너뛴다.
- 실행 중 실제로 겪은 문제 2건:
  1. **8프로세스 동시 실행 시 메모리 부족**(`numpy._ArrayMemoryError`,
     이 머신 물리 RAM 7.9GB) → `min(os.cpu_count(), 4)`로 워커 수 조정.
  2. **건물 폴리곤 위상 오류 45건**이 `shadow.py`의 `union()` 호출에서
     `GEOSException: TopologyException`을 유발 → `buildings.py`에
     `shapely.make_valid()`로 로딩 단계에서 자동 복구하는 로직 추가.
- **파일럿 검증**(구 2개, 버킷 3개)으로 실측 시간을 먼저 뽑아 추정치를
  갱신(승인된 20~40분 → 실측 기반 60~90분)한 뒤 전체 실행 진행.
- **최종 실행 결과: 63.9분** (승인받은 추정 범위 안).

### 2-4. 실행 결과물 (`data/out/`)

| 파일 | 크기 | 비고 |
|---|---|---|
| `edges.geojson` | 267.3MB | 1,145,124개 엣지, EPSG:4326 |
| `buildings.geojson` | ~379MB | 695,769건 (위상 오류 45건 복구됨) |
| `nodes.bin` | 11.5MB | 501,728개 노드, 빅엔디안 |
| `shade_table_{4개 대표일}.bin` | 각 33.9MB (합계 135.4MB) | 31버킷×1,145,124엣지, uint8 |
| `shade_meta.json` | — | n_edges=1145124, n_buckets=31, bucket_minutes=30 |

## 3. 서버 (`src/main/java/com/afterglow/shaderoute/`)

### 3-1. `nearestNode()` 를 KD-tree로 교체 + 스냅 거리 검증

- 기존: 노드 8,790개(강남구) 선형 스캔으로 충분히 빨랐으나, 서울 전체로
  노드가 501,728개로 늘면서 교체가 필요해짐.
- `build.gradle`에 `org.locationtech.jts:jts-core` 추가.
  `org.locationtech.jts.index.kdtree.KdTree`로 후보를 좁힌 뒤
  기존 `haversineMeters()`로 정확한 최근접 노드를 확정(위경도는 미터 단위로
  등방적이지 않아 KD-tree는 서울 중심 위도 기준 로컬 등장방형 근사 좌표로
  구성 — 후보 압축용일 뿐 최종 거리는 haversine).
- **새 파일 `nodes.bin`**: `int64 node_id | float64 lon | float64 lat` 고정
  24바이트 레코드, 빅엔디안(Java `DataInputStream` 기본값과 맞춰
  `ByteBuffer.order()` 래핑 불필요하게 함).
- **`MAX_SNAP_DISTANCE_M`(기본 300m, `shade.max-snap-distance-m`) 초과 시
  `NodeSnapTooFarException`(HTTP 422)**을 던지도록 추가 — 이전 세션에서
  발견한 "종로구 클릭 → 강남구로 조용히 잘못 스냅되고 0m/0% 그늘 경로가
  반환되던" 버그의 근본 원인(거리 제한 없는 최근접 스캔)을 해결.
- `RouteController`에 요청 단위 로깅(from/to/at/스냅 거리, 실패 사유) 추가
  — 기존엔 이런 로그가 전혀 없어 위 버그가 로그로도 안 남았음.

### 3-2. 서버 부팅 중 발견한 메모리 문제 (Jackson 트리 파싱 OOM)

- 서울 전체 데이터(280MB `edges.geojson`, 114만 피처)로 첫 부팅 시
  `ShadeGraph.load()`에서 `java.lang.OutOfMemoryError: Java heap space`
  발생. `objectMapper.readTree(file)`로 전체를 Jackson 트리 모델에 한 번에
  올리면 JsonNode 객체 오버헤드가 누적되어 JVM 기본 힙(~2GB, 이 머신
  7.9GB RAM의 1/4)을 넘음.
- **수정**: `JsonParser`로 최상위를 토큰 단위로 훑다가 `"features"` 배열
  원소 하나씩만 `objectMapper.readTree(parser)`로 파싱하는 스트리밍 방식으로
  `loadEdges()`를 재작성. 피처 1개 분량만 메모리에 머물게 해 OOM 해소.

### 3-3. 프론트 에러 메시지 개선

- `index.html`의 `fetchRoute()`가 기존엔 `!res.ok`면 `res.status`만 담아
  `"경로를 찾을 수 없습니다 (HTTP 422)"`처럼 일반 메시지만 보여줬음 — 서버가
  `{"error": "..."}`로 구체적인 사유(예: 스냅 거리 초과)를 내려주는데도
  버려지고 있었다.
- 응답이 실패(non-2xx)면 `res.json()`으로 바디를 파싱해 `error` 필드를
  상태 패널에 그대로 표시하도록 수정, 바디 파싱 자체가 실패하는 경우(네트워크
  에러 등)엔 기존처럼 일반 메시지로 폴백.

## 4. 검증

### 4-1. 브라우저 실측 (claude-in-chrome)

- **강남 → 종로 → 마포 → 성북** 4개 지역 이동하며 건물+그림자 렌더링 확인 —
  전부 정상. (종로는 정오라 그림자가 짧아 화면상 안 보였지만 `shadows` 소스에
  11,074개 폴리곤이 실제로 계산되어 있음을 JS로 확인.)
- **구 경계(강남구↔서초구) 그늘 비율 연속성**:
  - 정량: 경계 0~50m 구간 평균 그늘비율 0.149, 200~400m 안쪽 구간 0.145 —
    버퍼가 없었다면 경계 근처가 확연히 낮게 나왔을 텐데 그런 패턴 없음
    (103,982개 후보 엣지 기준).
  - 육안: 경계를 가로지르는 실제 경로(712m, 그늘 81%, 17:00)를 지도에서
    클릭해 생성 — 그림자가 경계선 앞뒤로 끊김 없이 이어짐.
- **제주도 좌표 → 422 에러 UI 노출**: 수정 전엔 `"경로를 찾을 수 없습니다
  (HTTP 422)"`만 표시됨을 확인. `fetchRoute()` 수정 후 재확인 —
  `"좌표(33.499600, 126.531200)에서 가장 가까운 노드까지 거리가 Infinitym로
  허용 임계값(300.0m)을 초과합니다"`가 그대로 표시되고, 3줄로 자연스럽게
  줄바꿈되며 레이아웃이 깨지지 않음(`overflow: visible`,
  `scrollWidth === clientWidth` 확인).
- `buildings.pmtiles` 재생성·배포(위상 오류 45건 복구가 반영되도록).

### 4-2. JVM 힙 사용량 실측 (`-Xmx` 지정 없이 기본값 부팅)

`jcmd <pid> GC.run` 강제 실행 후 `GC.heap_info`:

```
garbage-first heap   total 988160K, used 555014K
Metaspace       used 76083K, committed 76928K
```

| 항목 | 값 |
|---|---|
| 원본 파일 합계 (edges.geojson+nodes.bin+shade_table×4) | 414.2MB |
| G1 힙 used (GC 후) | 542.0MB |
| 원본 대비 배율 | **1.31배** (경고 기준 3배 미만 — 정상) |
| OS 레벨 프로세스 Working Set | 약 967MB |
| OS 레벨 커밋 메모리(페이지파일 사용량) | 약 1.17GB |
| 부팅 완료 시간 | **27.9초** (`Started AfterglowBeApplication in 27.891 seconds`) |

배포 EC2 인스턴스의 실제 RAM 용량은 리포에 문서화돼 있지 않아(secrets로
관리) 이번 세션에서 확인하지 못함 — `free -h`로 직접 확인 필요.
967MB~1.2GB 프로세스 발자국 기준, 1GB 인스턴스(t2/t3.micro)는 위험,
2GB 이상이면 대체로 충분.

## 5. 알려진 한계 / 후속 과제

- **좌표 이상치 2건 미제거**: 대한민국 국가 범위(경도 124~132, 위도 33~43)로
  검증했지만, 이전에 발견한 "강북구 주소인데 좌표는 부산/울산 인근" 이상치
  2건은 이 범위 안이라 안 걸러짐. 서울 범위로 좁히거나 별도 검증이 필요.
- **`simplify_graph` 생략**: 엣지가 osmnx 대비 약 2배로 잘게 쪼개져 있음
  (교차로가 아닌 중간 노드도 별도 엣지). 라우팅/그림자 계산 정확성엔
  문제없지만 `shade_table` 파일이 그만큼 크고 Dijkstra가 순회할 엣지가
  많음. 메모리가 확보되면(예: WSL 메모리 할당 상향, 또는 구별 추출 후
  merge 단계에 simplify를 넣는 방식) 재검토 가능.
- **EC2 인스턴스 메모리 여유 미확인**: 위 4-2 참고, 실제 배포 인스턴스
  RAM을 확인해 필요시 `-Xmx` 상한을 명시적으로 거는 걸 검토해야 함.
- **버퍼 반경(1000m) 고정값**: `HEIGHT_MAX_M=300` 건물이 태양고도 ~17도일 때
  그림자 길이 근사치로 잡은 값. 더 낮은 고도(3도 컷오프 근처)의 극단적으로
  긴 그림자는 여전히 놓칠 수 있음 — 기존 강남구 단일 실행도 자체 경계에서
  동일한 단순화가 있었던 것과 같은 성격의 한계.
- **대표 날짜 4개 고정**: 이전 리포와 동일한 한계 — 춘분/하지/추분/동지
  기준이라 다른 날짜의 실제 그림자와는 다를 수 있음.
- **`street_trees.csv` 미사용**: 이전 리포와 동일 — 가로수 그늘은 아직
  미구현.

## 6. 산출물 목록

| 경로 | 내용 |
|---|---|
| `batch/src/fetch_osm_pbf.py` | Geofabrik 한국 pbf 다운로드 |
| `batch/src/boundaries.py` | 서울+자치구 25개 경계 폴리곤 로더 |
| `batch/src/wsl_extract_network.py` | WSL 전용 pyrosm 추출 스크립트 (재개 가능) |
| `batch/src/graph.py` | 서울 보행망 로더(WSL 결과 읽기 전용) + 샘플링 |
| `batch/src/district.py` | 자치구 25개 병렬 작업 분할 + 캐싱 |
| `batch/src/main.py` | 서울 전체 오케스트레이션 (multiprocessing) |
| `batch/src/shadow.py` | 그림자 계산 + 버킷/대표일 상수 + `compute_shade_table` |
| `batch/src/make_tiles.sh` | buildings.geojson → buildings.pmtiles (WSL tippecanoe) |
| `batch/debug/coord_outliers.csv` | 좌표 범위 이상치 목록 (현재 0건) |
| `data/out/edges.geojson` | 서울 전체 보행 엣지 (1,145,124개) |
| `data/out/buildings.geojson` | 서울 전체 건물 (695,769건) |
| `data/out/nodes.bin` | 서울 전체 노드 좌표 (501,728개, KD-tree용) |
| `data/out/shade_table_{4개 대표일}.bin` | 그늘 비율 테이블 (31버킷×1,145,124엣지) |
| `src/main/resources/static/data/buildings.pmtiles` | 프론트 서빙용 벡터 타일 |
| `src/main/java/com/afterglow/shaderoute/graph/ShadeGraph.java` | KD-tree, 스트리밍 파싱 |
| `src/main/java/com/afterglow/shaderoute/graph/NodeSnapTooFarException.java` | 스냅 거리 초과 예외 |
| `src/main/resources/static/index.html` | pmtiles 전환, 에러 메시지 개선 |
