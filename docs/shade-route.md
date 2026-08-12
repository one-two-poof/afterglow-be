# 강남구 그늘길 라우팅 프로토타입

## 목표
강남구 보행 경로를 "그늘이 많은 길" 기준으로 탐색하는 프로토타입.
비용 최소화가 최우선 원칙. DB 없이 인메모리로 동작한다.

## 핵심 설계 (변경하지 말 것)
- 그늘 정보는 **오프라인에서 사전 계산**한다. 서버는 런타임에 기하 연산을 하지 않는다.
- 시간은 15분 버킷. 05:00~20:00 = 61개 버킷.
- 그늘 비율은 uint8 (0~255)로 저장. 최종 산출물은 `shade_table` (버킷 × 엣지).
- 서버는 Spring Boot 단일 인스턴스, 부팅 시 전체를 힙에 로드. DB 없음.
- 지도 위 그림자 폴리곤은 **브라우저에서** 계산한다. 서버는 건물 footprint+높이만 준다.

## 데이터 (실제 확인된 스키마)
### data/raw/bld_seoul.shp
- CRS: EPSG:5186 (중부원점, 미터 단위)
- 컬럼: <여기에 실제 dtypes 출력 붙여넣기>
- HEIGHT 필드는 0 또는 비정상값이 섞여 있음 → 2~300m 범위 밖이면 GRND_FLR * 3.3 으로 폴백
- 강남구 필터: SIG_CD == '11680'

### data/raw/street_trees.csv
- 컬럼: <실제 컬럼 붙여넣기>
- 수관 반경은 데이터에 없음 → 일괄 2.5m 버퍼, 높이 7m로 가정

## 좌표계 규칙 (버그 1순위)
- **모든 기하 연산은 EPSG:5186에서 한다.** 그림자 이동 거리는 미터 단위.
- 파일로 내보낼 때만 EPSG:4326으로 변환한다.
- WGS84 상태에서 buffer(), translate() 를 호출하는 코드는 무조건 버그다.

## 검증 규칙 (필수)
모든 지리 연산 단계는 `batch/debug/` 에 확인용 산출물을 남긴다.
- 그림자 계산 → matplotlib으로 PNG 저장 (건물 회색, 그림자 반투명)
- 정오와 17:00 두 시각을 나란히 그려서 그림자 방향이 반대인지 보이게 할 것
- 숫자만 출력하고 끝내지 말 것

## 기술 스택
- 배치: Python 3.11, geopandas, shapely 2.x, osmnx, pvlib, numpy
- 서버: Java 21, Spring Boot 3.x, Gradle. **PostGIS/JPA 쓰지 말 것**
- 프론트: MapLibre GL JS, suncalc, turf. 빌드 도구 없이 단일 HTML

## 하지 말 것
- 데이터베이스 도입 제안
- 네이버/카카오/구글 지도 SDK 사용
- 강남구 밖으로 범위 확장
- 테스트 프레임워크 대규모 도입 (프로토타입이다)

## 명령어
- 배치 실행: `cd batch && python -m src.main`
- 서버: 리포 루트 Afterglow 백엔드에 `com.afterglow.shaderoute` 패키지로 흡수됨 (`src/main/java/com/afterglow/shaderoute`, 정적 프론트는 `src/main/resources/static/index.html`). `./gradlew bootRun` (포트 8080) 하나로 같이 뜬다. 독립 `server/` 프로젝트는 더 이상 없음.