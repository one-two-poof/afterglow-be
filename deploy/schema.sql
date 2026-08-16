-- 추천 코스 응답 저장용 테이블 (Spring Boot 앱과 FastAPI ML 서버가 같은 Postgres를 공유)
-- 테이블 간 FOREIGN KEY 제약은 걸지 않음 (두 서비스가 독립적으로 insert하기 때문).
-- 컬럼명/타입은 src/main/java/com/afterglow/domain/RecommendationResult.java,
-- RecommendedCourse.java, CoursePlace.java의 JPA 매핑과 동일하게 맞춤
-- (Spring Boot가 먼저 떠서 Hibernate ddl-auto=update로 만들어도 같은 스키마가 나옴).

CREATE TABLE IF NOT EXISTS recommendation_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512),
    requested_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS recommended_courses (
    id BIGSERIAL PRIMARY KEY,
    recommendation_result_id BIGINT NOT NULL,
    rank INT NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    predicted_rating NUMERIC(4, 2) NOT NULL,
    total_distance_km NUMERIC(6, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS course_places (
    id BIGSERIAL PRIMARY KEY,
    recommended_course_id BIGINT NOT NULL,
    visit_order INT NOT NULL,
    place_name VARCHAR(256) NOT NULL,
    place_category VARCHAR(64) NOT NULL,
    is_indoor BOOLEAN NOT NULL,
    walk_hard INT NOT NULL
);

-- 조회 패턴(사용자별 이력, 응답별 코스, 코스별 장소)에 맞춘 인덱스.
-- FK 제약은 아니지만 join/필터 성능을 위해 인덱스는 걸어둠.
CREATE INDEX IF NOT EXISTS idx_recommendation_results_user_id
    ON recommendation_results (user_id);
CREATE INDEX IF NOT EXISTS idx_recommended_courses_result_id
    ON recommended_courses (recommendation_result_id);
CREATE INDEX IF NOT EXISTS idx_course_places_course_id
    ON course_places (recommended_course_id);

-- 추천 결과에 대한 사용자 피드백/라벨 (ML 학습 데이터).
-- Spring 쪽 numeric user_id 대신 원본 JWT 문자열을 그대로 저장 —
-- FastAPI가 users 테이블에 접근하지 않고도 독립적으로 기록할 수 있게 하기 위함.
CREATE TABLE IF NOT EXISTS recommendation_feedback (
    id BIGSERIAL PRIMARY KEY,
    jwt TEXT NOT NULL,
    course_id VARCHAR(32) NOT NULL,
    label INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_recommendation_feedback_course_id
    ON recommendation_feedback (course_id);
