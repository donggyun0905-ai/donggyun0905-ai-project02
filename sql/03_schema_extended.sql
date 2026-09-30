-- 스펙 오디세이 (Spec Odyssey) — 2주차 이후 확장 스키마
-- 대상: 1주차(sql/01_schema.sql) 8개 테이블을 제외한 나머지 28개 테이블 (분석·로드맵·대시보드·미션·면접관·부가기능)
-- 기준 문서: docs/db-design.md
-- 문자셋: utf8mb4 / utf8mb4_unicode_ci
-- 공통 컬럼(created_at, updated_at, is_deleted)은 모든 테이블에 포함. 물리 삭제 금지.
-- FK 삭제 정책: 전부 ON DELETE RESTRICT ON UPDATE CASCADE로 통일 (01_schema.sql과 동일).
-- 참고: 이 파일의 테이블들은 스키마만 선점한 상태이며, 실제 읽기/쓰기 로직(서비스·컨트롤러·JSP)은
--       각 기능(2주차 이후)을 실제로 만드는 담당자가 채운다. DAO 메서드도 지금 필요한 최소 집합만 넣었다.

SET NAMES utf8mb4;

-- =========================================================
-- SURVEY_QUESTION (설문 문항 마스터) — 신설
-- 관련 요구사항: FR-38 직무 발굴 + TD-5(d) 자가진단
-- 직무 발굴(JOB_DISCOVERY)과 자가진단(SELF_CHECK) 문항을 survey_type으로 구분해 한 테이블에 둔다.
-- =========================================================
CREATE TABLE SURVEY_QUESTION (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    survey_type         VARCHAR(20)  NOT NULL, -- JOB_DISCOVERY / SELF_CHECK
    content             VARCHAR(255) NOT NULL,
    job_category_hint   VARCHAR(50)  NULL,
    score_weight        INT          NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- PROBLEM (코테 문제 풀) — 신설
-- 관련 요구사항: FR-51~53 일일 미션 + TD-3 코테 문제 소스
-- =========================================================
CREATE TABLE PROBLEM (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    title             VARCHAR(200) NOT NULL,
    description       TEXT         NULL,
    difficulty_level  INT          NOT NULL,
    source_type       VARCHAR(20)  NOT NULL, -- AI_GENERATED / EXTERNAL_LINK / OPEN_DATASET
    external_url      VARCHAR(500) NULL,
    answer_key        TEXT         NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TREND_TECH (트렌드 기술 사이드바) — 신설
-- 관련 요구사항: FR-54 트렌드 기술 노출
-- =========================================================
CREATE TABLE TREND_TECH (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tech_name      VARCHAR(100) NOT NULL,
    summary        TEXT         NULL,
    source_url     VARCHAR(500) NULL,
    published_at   DATETIME     NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- LEVEL_TIER (등급 마스터) — 신설
-- 관련 요구사항: TD-5 레벨/스코어링
-- 복합 UNIQUE: (min_score) — 등급 구간이 겹치지 않게
-- =========================================================
CREATE TABLE LEVEL_TIER (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    min_score           INT          NOT NULL,
    max_score           INT          NULL, -- 최고 등급은 NULL(상한 없음)
    tier_name           VARCHAR(50)  NOT NULL,
    title_name          VARCHAR(50)  NOT NULL,
    problem_level_min   INT          NOT NULL,
    problem_level_max   INT          NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_level_tier_min_score (min_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- CERT_SCHEDULE (자격증 시험 일정) — 신설
-- 관련 요구사항: FR-71 D-day 자동 생성
-- =========================================================
CREATE TABLE CERT_SCHEDULE (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    certification_id    BIGINT       NOT NULL,
    round_name          VARCHAR(50)  NOT NULL,
    apply_start         DATE         NOT NULL,
    apply_end           DATE         NOT NULL,
    exam_date           DATE         NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_cert_schedule_certification_id (certification_id),
    CONSTRAINT fk_cert_schedule_certification
        FOREIGN KEY (certification_id) REFERENCES CERTIFICATION (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_REQUIRED_SKILL (직무별 요구 기술) — 신설 (기존 JSON 대체)
-- 관련 요구사항: FR-31 격차 분석 (규칙기반 대조)
-- 복합 UNIQUE: (job_id, skill_id) — 재수집 시 같은 요구 기술이 중복 누적되지 않게
-- =========================================================
CREATE TABLE JOB_REQUIRED_SKILL (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    job_id            BIGINT        NOT NULL,
    skill_id          BIGINT        NOT NULL,
    importance        VARCHAR(10)   NOT NULL, -- REQUIRED / PREFERRED
    required_level    VARCHAR(20)   NULL,
    source            VARCHAR(20)   NOT NULL, -- WORKNET / LLM / MANUAL
    is_estimated      BOOLEAN       NOT NULL DEFAULT FALSE,
    collected_at      DATETIME      NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_required_skill_job_skill (job_id, skill_id),
    KEY idx_job_required_skill_skill_id (skill_id),
    CONSTRAINT fk_job_required_skill_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_job_required_skill_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_POSTING (채용공고) — 신설
-- 관련 요구사항: FR-113 데이터 없는 직무 보완 (On-demand 조회 결과 공고 0건 시 LLM 일반화 요구스펙 보완의 근거 데이터)
-- 담당: B(로드맵) — 최초 36개 테이블 스캐폴딩(2주차 이전) 시점엔 없었고, 팀 확인 결과
-- 담당자가 안 정해져 있던 테이블이라 이번에 추가한다. .env의 WORK24_JOB_POSTING_API_KEY가
-- 고용24 채용정보 API 연동을 염두에 두고 이미 발급돼 있었음(실제 수집 배치는 별도 작업).
--
-- 컬럼은 실제 수집 대시보드 샘플(docs/saved_resource.html, 원티드·고용24 등 10개 출처
-- 1,606건 집계)을 보고 다시 확정했다 — 처음엔 개별 공고 상세 7개 필드만 생각했는데,
-- 실제 목록 화면 기준으로 회사명·기술스택·경력·지역·마감일·등록일·출처 시스템명이 더 있었다.
-- 지금은 "있는 데이터를 다 담아두는" 단계라 필드를 넉넉히 두고, 실제 화면에 보여줄 항목은
-- 나중에 조회 쿼리/화면 쪽에서 추린다(팀 방침, 2026-09-29).
-- =========================================================
CREATE TABLE JOB_POSTING (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    job_id            BIGINT        NOT NULL,
    source            VARCHAR(50)   NOT NULL, -- 출처 시스템명 (원티드/고용24/CSI/CJK/CAT/CIN/KOS/MIT/PRD/CWK 등)
    source_url        VARCHAR(500)  NULL,     -- 출처 원문 링크("보기") — 재수집 시 중복 저장 방지용 키
    title             VARCHAR(200)  NOT NULL, -- 명칭(제목)
    company_name      VARCHAR(150)  NULL,     -- 회사명
    summary           TEXT          NULL,     -- 무슨 일을 하는지 요약 (상세 페이지 전용, 목록에는 없을 수 있음)
    tech_stack        TEXT          NULL,     -- 기술스택 원문 목록 — 콤마 구분 텍스트(정규화는 다음 단계)
    qualifications    TEXT          NULL,     -- 자격요건
    preferred         TEXT          NULL,     -- 우대사항
    career_level      VARCHAR(50)   NULL,     -- 경력 (예: 경력, 경력무관, 신입, 경력8년)
    education_level   VARCHAR(50)   NULL,     -- 학력 (예: 학력무관, 대졸(4년))
    salary            VARCHAR(100)  NULL,     -- 급여 — 범위·"회사내규에 따름" 등 텍스트 혼재라 문자열로 둠
    region            VARCHAR(100)  NULL,     -- 지역 (예: 서울 강남구, 지역무관)
    deadline          VARCHAR(50)   NULL,     -- 마감 — "상시"처럼 날짜가 아닌 값도 있어 DATE 대신 문자열로 둠
    posted_at         DATE          NULL,     -- 원문 사이트 등록일 (우리가 수집한 시각인 collected_at과 별개)
    collected_at      DATETIME      NULL,     -- 우리 배치가 이 행을 수집한 시각
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_posting_source_url (source_url),
    KEY idx_job_posting_job_id (job_id),
    CONSTRAINT fk_job_posting_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_BENCHMARK_SPEC (합격자 스펙 역산, LLM 생성) — 신설
-- 관련 요구사항: FR-46 데이터 인사이트
-- =========================================================
CREATE TABLE JOB_BENCHMARK_SPEC (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    job_id          BIGINT        NOT NULL,
    tier            VARCHAR(20)   NOT NULL, -- ENTRY / CORE / ADVANCED / EXPERT
    spec_type       VARCHAR(20)   NOT NULL, -- CERT / PROJECT / SKILL / LANGUAGE
    content         VARCHAR(255)  NOT NULL,
    is_estimated    BOOLEAN       NOT NULL DEFAULT TRUE, -- 실데이터 없이 LLM이 생성 — 항상 true
    generated_at    DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_job_benchmark_spec_job_id (job_id),
    CONSTRAINT fk_job_benchmark_spec_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TREND_TECH_JOB (트렌드 기술 ↔ 직무 연관, N:M) — 신설
-- 관련 요구사항: FR-55 사이드바 직무 연관 필터링
-- 복합 UNIQUE: (trend_tech_id, job_id) — 같은 기술-직무 연결 중복 방지
-- =========================================================
CREATE TABLE TREND_TECH_JOB (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    trend_tech_id     BIGINT        NOT NULL,
    job_id            BIGINT        NOT NULL,
    relevance_score   DECIMAL(5,4)  NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trend_tech_job_trend_tech_job (trend_tech_id, job_id),
    KEY idx_trend_tech_job_job_id (job_id),
    CONSTRAINT fk_trend_tech_job_trend_tech
        FOREIGN KEY (trend_tech_id) REFERENCES TREND_TECH (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_trend_tech_job_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- GAP_ANALYSIS (격차 분석) — 신설
-- 관련 요구사항: FR-31 · 37 · 42
-- =========================================================
CREATE TABLE GAP_ANALYSIS (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    job_id         BIGINT        NOT NULL,
    match_rate     DECIMAL(5,2)  NOT NULL,
    analyzed_at    DATETIME      NOT NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_gap_analysis_user_id (user_id),
    KEY idx_gap_analysis_job_id (job_id),
    CONSTRAINT fk_gap_analysis_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_gap_analysis_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- GAP_ANALYSIS_ITEM (격차 분석 항목별 충족/부족) — 신설 (기존 JSON 대체)
-- 관련 요구사항: FR-42 비교표, FR-48 부족 역량 히트맵
-- =========================================================
CREATE TABLE GAP_ANALYSIS_ITEM (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    gap_analysis_id     BIGINT        NOT NULL,
    skill_id            BIGINT        NOT NULL,
    status              VARCHAR(10)   NOT NULL, -- MET / MISSING
    similarity_score    DECIMAL(5,4)  NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_gap_analysis_item_gap_analysis_id (gap_analysis_id),
    KEY idx_gap_analysis_item_skill_id (skill_id),
    CONSTRAINT fk_gap_analysis_item_gap_analysis
        FOREIGN KEY (gap_analysis_id) REFERENCES GAP_ANALYSIS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_gap_analysis_item_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- ROADMAP (여정 로드맵) — 신설
-- 관련 요구사항: FR-32 · 36 · 37
-- 복합 UNIQUE: (gap_analysis_id) — 격차 분석 1건에 로드맵 1건(1:1)
-- =========================================================
CREATE TABLE ROADMAP (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    gap_analysis_id    BIGINT        NOT NULL,
    version            INT           NOT NULL DEFAULT 1,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    is_primary         BOOLEAN       NOT NULL DEFAULT FALSE,
    target_level       VARCHAR(20)   NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roadmap_gap_analysis_id (gap_analysis_id),
    KEY idx_roadmap_user_id (user_id),
    CONSTRAINT fk_roadmap_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_roadmap_gap_analysis
        FOREIGN KEY (gap_analysis_id) REFERENCES GAP_ANALYSIS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- ROADMAP_STEP (로드맵 단계) — 신설
-- 관련 요구사항: FR-32 · 33 · 36
-- =========================================================
CREATE TABLE ROADMAP_STEP (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    roadmap_id          BIGINT        NOT NULL,
    step_order          INT           NOT NULL,
    step_type           VARCHAR(20)   NOT NULL, -- CERT / PROJECT / SKILL
    tier                VARCHAR(20)   NOT NULL, -- ENTRY / CORE / ADVANCED / EXPERT
    certification_id    BIGINT        NULL,
    related_skill_id    BIGINT        NULL,
    reason              TEXT          NULL,
    -- SKILL 단계 학습 검증(규칙 기반, 2026-09-30 팀 결정) — proof_type으로 티어별 증빙 방식을
    -- 명시적으로 저장한다: ENTRY는 NOTE(공부노트), CORE/ADVANCED는 PROJECT_LINK(프로젝트 등록/업그레이드),
    -- EXPERT는 TEACHING_POST(기술 설명 글). proof_content는 NOTE·TEACHING_POST의 제출 원문을
    -- 직접 저장한다 — db-design 원안은 DOCUMENTS 테이블 재사용이었으나, 파일이 아닌 순수 텍스트라
    -- 업로드 파이프라인을 타지 않고 TEXT 컬럼에 바로 저장하는 쪽으로 단순화했다(팀 확인 필요, 2026-09-30).
    proof_type          VARCHAR(20)   NULL, -- NOTE / PROJECT_LINK / TEACHING_POST
    proof_content       TEXT          NULL,
    evidence_project_id BIGINT        NULL, -- PROJECT_LINK일 때 어느 프로젝트로 완료했는지
    review_status       VARCHAR(20)   NULL, -- PENDING / PASSED / NEEDS_REVISION
    review_note         TEXT          NULL, -- 규칙 판정 근거·피드백
    is_completed        BOOLEAN       NOT NULL DEFAULT FALSE,
    completed_at        DATETIME      NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_roadmap_step_roadmap_id (roadmap_id),
    KEY idx_roadmap_step_certification_id (certification_id),
    KEY idx_roadmap_step_related_skill_id (related_skill_id),
    KEY idx_roadmap_step_evidence_project_id (evidence_project_id),
    CONSTRAINT fk_roadmap_step_roadmap
        FOREIGN KEY (roadmap_id) REFERENCES ROADMAP (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_roadmap_step_certification
        FOREIGN KEY (certification_id) REFERENCES CERTIFICATION (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_roadmap_step_skill
        FOREIGN KEY (related_skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_roadmap_step_evidence_project
        FOREIGN KEY (evidence_project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_RECOMMENDATION (직무 발굴 추천) — 신설
-- 관련 요구사항: FR-34 · 35 · 38 · 39
-- 복합 UNIQUE: (user_id, job_id) — 같은 직무 중복 추천 방지
-- =========================================================
CREATE TABLE JOB_RECOMMENDATION (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    job_id          BIGINT        NOT NULL,
    rank_order      INT           NOT NULL,
    match_reason    TEXT          NULL,
    summary_json    TEXT          NULL, -- 하는 일/필요 역량/전망 — 화면 표시 전용 서술형 텍스트
    is_selected     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_recommendation_user_job (user_id, job_id),
    KEY idx_job_recommendation_job_id (job_id),
    CONSTRAINT fk_job_recommendation_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_job_recommendation_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_SURVEY_ANSWER (설문 응답) — 신설
-- 관련 요구사항: FR-38 + 자가진단(TD-5 d)
-- 복합 UNIQUE: (user_id, question_id) — 재응답은 갱신이지 새 줄 누적이 아님
-- =========================================================
CREATE TABLE USER_SURVEY_ANSWER (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    question_id     BIGINT      NOT NULL,
    answer_value    INT         NOT NULL,
    answered_at     DATETIME    NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_survey_answer_user_question (user_id, question_id),
    KEY idx_user_survey_answer_question_id (question_id),
    CONSTRAINT fk_user_survey_answer_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_user_survey_answer_question
        FOREIGN KEY (question_id) REFERENCES SURVEY_QUESTION (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- EXTERNAL_API_CACHE (외부 API 캐싱) — 신설
-- 관련 요구사항: FR-111 · 112, NFR-1
-- 복합 UNIQUE: (api_type, request_key) — 워크넷/LLM 요청이 같은 키로 서로 덮어쓰지 않게
-- =========================================================
CREATE TABLE EXTERNAL_API_CACHE (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    api_type          VARCHAR(20)  NOT NULL, -- WORKNET / LLM / EMBEDDING
    request_key       VARCHAR(255) NOT NULL,
    response_body     TEXT         NULL,
    status            VARCHAR(10)  NOT NULL, -- SUCCESS / FAILED
    cached_at         DATETIME     NOT NULL,
    expires_at        DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_api_cache_type_key (api_type, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SPEC_SCORE_HISTORY (스펙 완성도 스냅샷) — 신설
-- 관련 요구사항: FR-41 · 45 · 84
-- =========================================================
CREATE TABLE SPEC_SCORE_HISTORY (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    snapshot_date          DATE          NOT NULL,
    completeness_score     DECIMAL(5,2)  NOT NULL,
    major                  VARCHAR(50)   NULL,
    grade                  VARCHAR(20)   NULL,
    is_seed                BOOLEAN       NOT NULL DEFAULT FALSE, -- 시연용 가상 데이터 여부
    created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_spec_score_history_user_id (user_id),
    CONSTRAINT fk_spec_score_history_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_SKILL_TREND (직무별 요구 기술 시계열 스냅샷) — 신설
-- 관련 요구사항: FR-47 시계열 그래프
-- 복합 UNIQUE: (job_id, skill_id, period_ym) — 같은 달 스냅샷 중복 방지
-- =========================================================
CREATE TABLE JOB_SKILL_TREND (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    job_id            BIGINT        NOT NULL,
    skill_id          BIGINT        NOT NULL,
    period_ym         CHAR(6)       NOT NULL, -- YYYYMM
    mention_count     INT           NOT NULL,
    mention_ratio     DECIMAL(5,2)  NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_skill_trend_job_skill_period (job_id, skill_id, period_ym),
    KEY idx_job_skill_trend_skill_id (skill_id),
    CONSTRAINT fk_job_skill_trend_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_job_skill_trend_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_DAILY_MISSION (일일 미션 배정) — 신설 (PROBLEM과 분리)
-- 관련 요구사항: FR-51~53
-- 복합 UNIQUE: (user_id, assigned_date, problem_id) — 같은 날 같은 문제 중복 배정 방지
-- is_correct는 완료 전까지 알 수 없으므로 NULL 허용.
-- =========================================================
CREATE TABLE USER_DAILY_MISSION (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    problem_id       BIGINT      NOT NULL,
    assigned_date    DATE        NOT NULL,
    is_completed     BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at     DATETIME    NULL,
    is_correct       BOOLEAN     NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_daily_mission_user_date_problem (user_id, assigned_date, problem_id),
    KEY idx_user_daily_mission_problem_id (problem_id),
    CONSTRAINT fk_user_daily_mission_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_user_daily_mission_problem
        FOREIGN KEY (problem_id) REFERENCES PROBLEM (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SCORE_LOG (점수 적립 이력, append-only) — 신설
-- 관련 요구사항: TD-5 스코어링
-- 복합 UNIQUE: (user_id, signal_type, ref_id) — 완료 체크를 껐다 켜도 중복 적립되지 않게
-- =========================================================
CREATE TABLE SCORE_LOG (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    signal_type    VARCHAR(20)  NOT NULL, -- ROADMAP / QUIZ / PROBLEM / DOCUMENT / SELF_CHECK
    ref_id         BIGINT       NOT NULL,
    points         INT          NOT NULL,
    earned_at      DATETIME     NOT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_log_user_signal_ref (user_id, signal_type, ref_id),
    CONSTRAINT fk_score_log_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_SCORE_SUMMARY (사용자별 점수 요약 캐시, 1:1) — 신설
-- 관련 요구사항: TD-5 스코어링
-- user_id는 AUTO_INCREMENT가 아니라 USERS.id를 그대로 받는 PK이자 FK (1:1 자동 보장).
-- =========================================================
CREATE TABLE USER_SCORE_SUMMARY (
    user_id             BIGINT      NOT NULL,
    total_score         INT         NOT NULL DEFAULT 0,
    current_tier_id     BIGINT      NULL,
    streak_count        INT         NOT NULL DEFAULT 0,
    last_mission_date   DATE        NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id),
    KEY idx_user_score_summary_current_tier_id (current_tier_id),
    CONSTRAINT fk_user_score_summary_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_user_score_summary_tier
        FOREIGN KEY (current_tier_id) REFERENCES LEVEL_TIER (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SHARE_LINK (면접관 공유 링크) — 신설
-- 관련 요구사항: FR-85 · 86, NFR-9
-- =========================================================
CREATE TABLE SHARE_LINK (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    token            VARCHAR(64)  NOT NULL, -- 추측 불가능한 랜덤 문자열(SecureRandom)
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at       DATETIME     NULL,
    scope_basic      BOOLEAN      NOT NULL DEFAULT TRUE,
    scope_skills     BOOLEAN      NOT NULL DEFAULT FALSE,
    scope_growth     BOOLEAN      NOT NULL DEFAULT FALSE,
    label            VARCHAR(50)  NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_link_token (token),
    KEY idx_share_link_user_id (user_id),
    CONSTRAINT fk_share_link_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SHARE_LINK_VIEW_LOG (공유 링크 열람 이력, append-only) — 신설
-- 관련 요구사항: NFR-9 비정상 접근 확인 근거
-- =========================================================
CREATE TABLE SHARE_LINK_VIEW_LOG (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    share_link_id     BIGINT       NOT NULL,
    viewed_at         DATETIME     NOT NULL,
    viewer_ip         VARCHAR(45)  NULL, -- IPv6 대응 45자
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_share_link_view_log_share_link_id (share_link_id),
    CONSTRAINT fk_share_link_view_log_share_link
        FOREIGN KEY (share_link_id) REFERENCES SHARE_LINK (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- EVALUATION_SESSION (면접관 비교 세션, 장바구니) — 신설
-- 관련 요구사항: FR-82
-- 면접관은 계정이 없으므로(FR-14) user_id 대신 브라우저 세션 토큰으로 소유를 식별한다.
-- =========================================================
CREATE TABLE EVALUATION_SESSION (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    session_token    VARCHAR(64)  NOT NULL,
    company_name     VARCHAR(100) NULL,
    expires_at       DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_session_token (session_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- EVALUATION_SESSION_ITEM (비교 대상 지원자 목록) — 신설
-- 관련 요구사항: FR-82
-- 복합 UNIQUE: (session_id, share_link_id) — 같은 지원자 중복 담기 방지
-- =========================================================
CREATE TABLE EVALUATION_SESSION_ITEM (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    session_id       BIGINT      NOT NULL,
    share_link_id    BIGINT      NOT NULL,
    added_at         DATETIME    NOT NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_session_item_session_share_link (session_id, share_link_id),
    KEY idx_evaluation_session_item_share_link_id (share_link_id),
    CONSTRAINT fk_evaluation_session_item_session
        FOREIGN KEY (session_id) REFERENCES EVALUATION_SESSION (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_evaluation_session_item_share_link
        FOREIGN KEY (share_link_id) REFERENCES SHARE_LINK (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- EVALUATION_CRITERIA (면접관 요구 역량 기준) — 신설
-- 관련 요구사항: FR-83
-- 복합 UNIQUE: (session_id, skill_id) — 같은 역량에 가중치 중복 등록 방지
-- =========================================================
CREATE TABLE EVALUATION_CRITERIA (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    session_id    BIGINT      NOT NULL,
    skill_id      BIGINT      NOT NULL,
    weight        INT         NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_criteria_session_skill (session_id, skill_id),
    KEY idx_evaluation_criteria_skill_id (skill_id),
    CONSTRAINT fk_evaluation_criteria_session
        FOREIGN KEY (session_id) REFERENCES EVALUATION_SESSION (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_evaluation_criteria_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- DOCUMENTS (서류 보관함) — 신설
-- 관련 요구사항: FR-61~64, NFR-7
-- =========================================================
CREATE TABLE DOCUMENTS (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    project_id       BIGINT       NULL, -- FR-63 특정 프로젝트와 연결(선택)
    -- 공부노트·기술 설명 글(PDF)을 프로젝트 없이 바로 SKILL 단계에 붙이기 위함
    -- (2026-09-30 팀 결정, ENTRY/EXPERT 학습 검증).
    roadmap_step_id  BIGINT       NULL,
    original_name    VARCHAR(255) NOT NULL,
    stored_name      VARCHAR(255) NOT NULL, -- 한글·중복 파일명 대응 저장명
    file_path        VARCHAR(500) NOT NULL,
    file_size        BIGINT       NOT NULL,
    mime_type        VARCHAR(100) NULL,
    checksum         VARCHAR(64)  NULL, -- 무결성 관리(NFR-7)
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_documents_user_id (user_id),
    KEY idx_documents_project_id (project_id),
    KEY idx_documents_roadmap_step_id (roadmap_step_id),
    CONSTRAINT fk_documents_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_documents_project
        FOREIGN KEY (project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_documents_roadmap_step
        FOREIGN KEY (roadmap_step_id) REFERENCES ROADMAP_STEP (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- DDAY_ALERT (마감 알림 / D-day) — 신설
-- 관련 요구사항: FR-71 · 72
-- 복합 UNIQUE: (user_id, cert_schedule_id) — 같은 시험 일정 D-day 중복 등록 방지
-- (CUSTOM 유형은 cert_schedule_id가 NULL이라 이 제약에 걸리지 않는다.)
-- =========================================================
CREATE TABLE DDAY_ALERT (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    cert_schedule_id     BIGINT       NULL,
    title                VARCHAR(100) NOT NULL,
    target_date          DATE         NOT NULL,
    alert_type           VARCHAR(20)  NOT NULL, -- CERT / RECRUIT / CUSTOM
    is_notified          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dday_alert_user_cert_schedule (user_id, cert_schedule_id),
    KEY idx_dday_alert_cert_schedule_id (cert_schedule_id),
    CONSTRAINT fk_dday_alert_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_dday_alert_cert_schedule
        FOREIGN KEY (cert_schedule_id) REFERENCES CERT_SCHEDULE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- AI_USAGE_LOG (AI 활용 역량 리포트, 자기제출) — 신설
-- 관련 요구사항: FR-101 · 102, NFR-4
-- =========================================================
CREATE TABLE AI_USAGE_LOG (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    user_id              BIGINT      NOT NULL,
    usage_record_json    TEXT        NULL,
    is_shared            BOOLEAN     NOT NULL DEFAULT FALSE, -- 공유 여부는 본인 선택(NFR-4)
    created_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_ai_usage_log_user_id (user_id),
    CONSTRAINT fk_ai_usage_log_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
