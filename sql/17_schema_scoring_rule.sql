-- 스펙 오디세이 (Spec Odyssey) — 점수·복습 주기 규칙 테이블: SCORING_RULE
-- 대상: 이미 DB를 만든 환경에 한 번 실행한다. (sql/03에도 같은 정의가 있어 새 DB는 03만 실행해도 된다.)
-- 기준 문서: docs/db-design.md "SCORING_RULE"
-- 로드맵 단계 점수·복습/유지 주기·감쇠 값을 코드 상수 대신 데이터로 둔다. 코드에는 같은 기본값이 있어
-- 행이 없거나 이 테이블이 아직 없어도 동작은 그대로다(값만 기본값). 일일 미션 배점은 여기서 다루지 않는다(코드 상수).

CREATE TABLE IF NOT EXISTS SCORING_RULE (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    rule_key    VARCHAR(50)   NOT NULL,
    rule_value  INT           NOT NULL,
    description VARCHAR(200)  NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scoring_rule_key (rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO SCORING_RULE (rule_key, rule_value, description) VALUES
    ('LADDER_BUDGET',               2500, '직무 하나의 기술 사다리(입문→전문가)를 끝까지 했을 때 받는 총점. 기술이 몇 개든 같다'),
    ('WEIGHT_TIER_ENTRY',              1, '단계 가중치 — 입문'),
    ('WEIGHT_TIER_CORE',               2, '단계 가중치 — 핵심'),
    ('WEIGHT_TIER_ADVANCED',           2, '단계 가중치 — 심화'),
    ('WEIGHT_TIER_EXPERT',             3, '단계 가중치 — 전문가'),
    ('WEIGHT_IMPORTANCE_REQUIRED',     2, '중요도 가중치 — 필수 기술'),
    ('WEIGHT_IMPORTANCE_PREFERRED',    1, '중요도 가중치 — 우대 기술'),
    ('STEP_POINTS_MIN',                5, '기술 단계 1개가 주는 최소 점수'),
    ('FIXED_STEP_POINTS',            100, '자격증·프로젝트 단계 점수(기술 사다리 밖)'),
    ('ROUND_SKILL_COUNT',              5, '로드맵 한 라운드에 담는 기술 수'),
    ('REVIEW_POINTS_BASE',            40, '기술 복습 첫 점수'),
    ('REVIEW_POINTS_DECAY',           10, '같은 기술 복습을 할 때마다 줄어드는 점수'),
    ('REVIEW_POINTS_MIN',              5, '기술 복습 최저 점수'),
    ('REVIEW_DAYS_ENTRY',             30, '복습 주기(일) — 최고 단계 입문'),
    ('REVIEW_DAYS_CORE',              60, '복습 주기(일) — 최고 단계 핵심'),
    ('REVIEW_DAYS_ADVANCED',          90, '복습 주기(일) — 최고 단계 심화'),
    ('REVIEW_DAYS_EXPERT',           120, '복습 주기(일) — 최고 단계 전문가'),
    ('PROJECT_UPDATE_DAYS',           90, '프로젝트 업데이트 단계가 생기는 주기(일)'),
    ('ARTICLE_UPDATE_DAYS',          150, '기술 글 업데이트 단계가 생기는 주기(일)'),
    ('TREND_STUDY_DAYS',              30, '트렌딩 학습 단계가 생기는 주기(일)'),
    ('PROJECT_UPDATE_POINTS_BASE',    60, '프로젝트 업데이트 첫 점수'),
    ('ARTICLE_UPDATE_POINTS_BASE',    60, '기술 글 업데이트 첫 점수'),
    ('TREND_STUDY_POINTS',            40, '트렌딩 학습 점수(고정)'),
    ('UPKEEP_POINTS_DECAY',           10, '유지 단계 감쇠 폭'),
    ('UPKEEP_POINTS_MIN',              5, '유지 단계 최저 점수');
