# afterglow-be

Notion 데이터베이스를 Spring Boot로 동기화하고, 프론트엔드에는 RDB 기반 REST API로 제공하는 백엔드입니다.

## 아키텍처

- **편집**: Notion UI
- **조회(기본)**: H2(dev) / PostgreSQL(prod)에 동기화된 `content_items`
- **동기화**: `@Scheduled` cron + `POST /api/sync` 수동 트리거
- **최신 조회(선택)**: `GET /api/items/{notionPageId}?fresh=true` → Notion API 1회 호출 후 DB upsert

## 환경 변수

| 변수 | 설명 |
|------|------|
| `NOTION_API_TOKEN` | Notion Integration Secret |
| `NOTION_DATABASE_ID` | Notion Database ID |
| `NOTION_TITLE_PROPERTY` | 제목 컬럼명 (기본: `Name`) |
| `NOTION_STATUS_PROPERTY` | 상태 select 컬럼명 (기본: `Status`) |
| `SYNC_CRON` | 동기화 cron (기본: 5분마다) |
| `DATABASE_URL` | prod 프로필 JDBC URL |

Notion DB 페이지에서 Integration을 **연결(Connect)** 해야 합니다.

## API

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/health` | 헬스 + Notion 설정 여부 |
| GET | `/api/hospitals` | **병원 DB 전체 목록** (Notion 컬럼 전체 `properties` 포함) |
| GET | `/api/items` | DB 목록 (`?includeArchived=true` 가능) |
| GET | `/api/items/{notionPageId}` | 단건 (`?fresh=true` 시 Notion 직접 조회) |
| POST | `/api/sync` | Notion → DB 수동 동기화 |

## Notion 병원 DB 설정

1. Notion에 **데이터베이스(표)** 생성 — 각 행 = 병원 1곳
2. **첫 번째 컬럼**은 `제목(title)` 타입 권장 → 병원명 (`Name` 또는 `병원명` 등)
3. Integration **`afterglow_token`** 에 DB 페이지 **Access** 연결
4. `application-local.properties`:
   - `notion.database-id` = DB ID (하이픈 있/없 모두 가능)
   - `notion.title-property` = 병원명 컬럼 **정확한 이름** (기본 `Name`)
   - `notion.status-property` = 상태 select 컬럼명 (없으면 생략 가능, `status`는 null)
5. 병원 행 추가 후 `POST /api/sync` → `GET /api/hospitals` 로 확인

응답 예시:

```json
[
  {
    "id": 1,
    "notionPageId": "...",
    "name": "OO병원",
    "status": "운영중",
    "properties": {
      "Name": "OO병원",
      "주소": "서울시 ...",
      "전화": "02-..."
    },
    "notionLastEditedAt": "...",
    "syncedAt": "..."
  }
]
```

## 실행 (로컬)

1. `src/main/resources/application-local.properties` 에 Notion 토큰·DB ID 설정 (Git 제외됨)
2. 아래 **한 가지**만 실행 (별도 `build` 불필요, 변경분만 자동 컴파일)

```powershell
# Windows — 가장 간단
.\run.ps1
```

또는 `.\gradlew.bat bootRun`

Cursor/VS Code: **Ctrl+Shift+B** (기본 빌드 태스크 = 서버 실행)

프로필: `local`(시크릿) + `dev`(H2 DB) 자동 적용

프로덕션: `--spring.profiles.active=prod` 와 PostgreSQL 환경 변수를 설정하세요.

## 프론트 연동 예시

```http
GET http://localhost:8080/api/items
GET http://localhost:8080/api/items/{page-id}?fresh=true
```

CORS 기본 허용: `http://localhost:3000`, `http://localhost:5173`
