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
    difficulty_level  INT          NOT NULL, -- 0~5 (0 = 프로그래머스 Lv.0 입문)
    category          VARCHAR(20)  NOT NULL DEFAULT 'ALGORITHM', -- SQL / ALGORITHM — 목표 직무별 출제 비율 기준
    source_type       VARCHAR(20)  NOT NULL, -- AI_GENERATED / EXTERNAL_LINK / OPEN_DATASET
    external_url      VARCHAR(500) NULL,
    answer_key        TEXT         NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_problem_category_level (category, difficulty_level)
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
    -- 이 분석이 어느 JOB.requirement_version 기준인지 저장 — 이후 JOB 쪽이 갱신되면 낡은 분석인지
    -- 판단하는 근거가 된다(2026-09-30 팀 결정).
    job_requirement_version INT  NULL,
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
    step_type           VARCHAR(20)   NOT NULL, -- CERT / PROJECT / SKILL / REVIEW
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
-- submitted_*: "정답 입력하기"로 제출한 풀이 코드 — 컴파일(문법) 확인을 통과한 마지막 제출분 (FR-53)
-- =========================================================
CREATE TABLE USER_DAILY_MISSION (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    user_id             BIGINT      NOT NULL,
    problem_id          BIGINT      NOT NULL,
    assigned_date       DATE        NOT NULL,
    is_completed        BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at        DATETIME    NULL,
    is_correct          BOOLEAN     NULL,
    submitted_code      MEDIUMTEXT  NULL,
    submitted_language  VARCHAR(20) NULL, -- JAVA / PYTHON / CPP / C / JAVASCRIPT / SQL
    submitted_at        DATETIME    NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
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
    scope_resume     BOOLEAN      NOT NULL DEFAULT FALSE, -- 이력서 파일(USERS.resume_document_id) 공개
    scope_cover_letter BOOLEAN    NOT NULL DEFAULT FALSE, -- 자소서 파일(USERS.cover_letter_document_id) 공개
    scope_age        BOOLEAN      NOT NULL DEFAULT FALSE, -- 나이(USERS.age) 공개 — 면접관 비교 화면의 나이순 정렬용
    scope_activity   BOOLEAN      NOT NULL DEFAULT FALSE, -- 활동 내역(잔디·타임라인) 공개 — sql/30
    scope_project_docs BOOLEAN    NOT NULL DEFAULT FALSE, -- 프로젝트 제출 서류(PROJECT_DOCUMENT_ITEM) 파일 공개 (31번)
    scope_education  BOOLEAN      NOT NULL DEFAULT FALSE, -- 학력(USER_EDUCATION) 공개 — 블라인드 채용 고려 (31번)
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
-- 면접관 계정(USERS.user_type = 'INTERVIEWER')의 비교 목록은 user_id로 소유를 식별한다.
-- user_id가 NULL이면 계정 없는 익명 세션으로, 브라우저 세션 토큰이 소유 증명이다.
-- UNIQUE(user_id): 면접관 한 명에 비교 목록 하나 (NULL은 여러 개 허용).
-- 이미 만든 DB에는 sql/07_alter_evaluation_session_user.sql을 실행한다.
-- =========================================================
CREATE TABLE EVALUATION_SESSION (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NULL,
    session_token    VARCHAR(64)  NOT NULL,
    company_name     VARCHAR(100) NULL,
    expires_at       DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_session_token (session_token),
    UNIQUE KEY uk_evaluation_session_user (user_id),
    CONSTRAINT fk_evaluation_session_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
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
    review_status    VARCHAR(20) NOT NULL DEFAULT 'REVIEWING', -- REVIEWING / PASS / HOLD / FAIL (31번)
    rating           TINYINT     NULL,                         -- 면접관 평점 1~5
    memo             TEXT        NULL,                         -- 면접관 메모 — 지원자에게 보이지 않음
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
    file_path        VARCHAR(500) NULL, -- 예전(디스크) 서류만 — 새 서류는 NULL (32번)
    file_size        BIGINT       NOT NULL,
    mime_type        VARCHAR(100) NULL,
    checksum         VARCHAR(64)  NULL, -- 무결성 관리(NFR-7)
    file_data        LONGBLOB     NULL, -- 파일 내용 — 어느 PC의 서버에서든 열리게 DB에 저장 (32번)
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

-- =========================================================
-- USERS.resume_document_id · cover_letter_document_id → DOCUMENTS (이력서·자소서 파일)
-- USERS(01_schema.sql)가 DOCUMENTS보다 먼저 만들어져서, FK는 DOCUMENTS가 생긴 뒤인 여기서 건다.
-- 이미 만든 DB에는 sql/09_alter_users_resume.sql(이력서), sql/12_alter_users_cover_letter.sql(자소서)을 실행한다.
-- =========================================================
ALTER TABLE USERS
    ADD CONSTRAINT fk_users_resume_document
        FOREIGN KEY (resume_document_id) REFERENCES DOCUMENTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    ADD CONSTRAINT fk_users_cover_letter_document
        FOREIGN KEY (cover_letter_document_id) REFERENCES DOCUMENTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- =========================================================
-- PROJECT_TECH_NOTE (프로젝트 기술 활용 설명서) — 신설
-- 프로젝트에 등록한 기술마다 "어떻게 활용했는지"를 문장으로 받는다 (개발일지 4-4, 2026-09-30 확정).
-- 딥러닝·시맨틱 보정용 재료라서 NFR-4(본인 동의)에 맞춰 consent_for_training을 같이 받는다.
-- 복합 UNIQUE: (project_id, skill_id) — 한 프로젝트에서 같은 기술의 설명서는 하나.
-- =========================================================
CREATE TABLE PROJECT_TECH_NOTE (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    project_id            BIGINT       NOT NULL,
    skill_id              BIGINT       NOT NULL,
    description           TEXT         NOT NULL, -- 이 기술을 어떻게 활용했는지
    consent_for_training  BOOLEAN      NOT NULL DEFAULT FALSE, -- 학습 데이터 활용 동의. 체크 안 해도 완료 처리에는 지장 없음
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_tech_note_project_skill (project_id, skill_id),
    KEY idx_project_tech_note_skill_id (skill_id),
    CONSTRAINT fk_project_tech_note_project
        FOREIGN KEY (project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_project_tech_note_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- PROJECT_DOCUMENT_ITEM (프로젝트 문서 체크리스트) — 신설
-- README·실행 화면 캡처·기획서·설계 문서·API 명세서·테스트 결과서·발표자료를 종류별로 "제출" 또는
-- "해당 없음"으로 받는다 (개발일지 4-4, 2026-09-30 확정). 실제 파일은 기존 DOCUMENTS를 재사용한다.
-- 완료 판정: doc_type이 README, SCREENSHOT인 두 행이 둘 다 SUBMITTED여야 로드맵 PROJECT 단계를 완료할 수 있다
-- (이 두 종류는 NOT_APPLICABLE로 둘 수 없다 — 애플리케이션 규칙).
-- 복합 UNIQUE: (project_id, doc_type)
-- =========================================================
CREATE TABLE PROJECT_DOCUMENT_ITEM (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    doc_type      VARCHAR(30)  NOT NULL, -- README / SCREENSHOT / PLANNING / DESIGN / API_SPEC / TEST_REPORT / PRESENTATION
    status        VARCHAR(20)  NOT NULL, -- SUBMITTED(제출) / NOT_APPLICABLE(해당 없음)
    document_id   BIGINT       NULL,     -- SUBMITTED일 때만 채운다 → DOCUMENTS
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_document_item_project_type (project_id, doc_type),
    KEY idx_project_document_item_document_id (document_id),
    CONSTRAINT fk_project_document_item_project
        FOREIGN KEY (project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_project_document_item_document
        FOREIGN KEY (document_id) REFERENCES DOCUMENTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- PROJECT_LINK (프로젝트 기타 링크) — 신설
-- 저장소(repo_url)·배포(deploy_url) 말고도 블로그 글, 발표 영상, 노션 등 프로젝트를 보여줄 링크가 더 필요할 수 있어서
-- 이름(label) + 주소(url)를 프로젝트당 최대 5개까지 받는다. 면접관 공유 타임라인에도 같이 보인다.
-- 수정은 "기존 줄을 지우고(is_deleted) 새로 넣는" 방식이라 UNIQUE를 두지 않는다. sort_order로 입력 순서를 지킨다.
-- 주소는 http/https만 허용한다 — 애플리케이션이 저장할 때와 면접관 화면에 보여줄 때 둘 다 확인한다.
-- =========================================================
CREATE TABLE PROJECT_LINK (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    label         VARCHAR(50)  NULL,     -- 링크 이름(예: 블로그 글, 발표 영상). 비우면 화면에서 주소의 도메인을 보여준다
    url           VARCHAR(500) NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_project_link_project_id (project_id),
    CONSTRAINT fk_project_link_project
        FOREIGN KEY (project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE (기술 글 — 게시판) — 신설
-- 로드맵 EXPERT 단계의 "기술 설명 글"을 서비스 안에서 블로그처럼 공개한다 (개발일지 4-3).
-- 규칙 판정(글자 수·키워드·링크)을 통과하면 곧바로 PUBLISHED, 문제가 있으면 팀이 나중에 HIDDEN으로 내린다.
-- 댓글·하트·북마크·조회수가 붙는 게시판이라서 아래 TECH_ARTICLE_* 5개 테이블이 이 글에 매달린다.
-- roadmap_step_id UNIQUE: EXPERT 단계 하나에서 글 하나. NULL(자유 글)은 여러 개 허용된다.
-- *_count 컬럼은 목록·정렬을 위한 집계값이다. 하트·댓글·북마크·조회가 일어나는 같은 트랜잭션에서 함께 갱신한다.
-- =========================================================
CREATE TABLE TECH_ARTICLE (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    skill_id         BIGINT       NULL,     -- 어떤 기술에 대한 글인지 — 다른 사용자가 이 기준으로 찾아본다. ARCHIVE_TIP은 NULL 가능
    roadmap_step_id  BIGINT       NULL,     -- EXPERT 단계에서 나온 글이면 그 단계. 자유 글이면 NULL
    source_type      VARCHAR(20)  NOT NULL DEFAULT 'ROADMAP_EXPERT', -- ROADMAP_EXPERT(로드맵 증빙) / FREE(자유 작성) / ARCHIVE_TIP(스펙 아카이브 팁)
    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT(임시저장) / PUBLISHED(공개) / HIDDEN(운영자가 내림)
    published_at     DATETIME     NULL,
    hidden_reason    VARCHAR(200) NULL,     -- 내린 이유 — 작성자에게 보여준다
    hidden_at        DATETIME     NULL,
    view_count       INT          NOT NULL DEFAULT 0,
    like_count       INT          NOT NULL DEFAULT 0,
    comment_count    INT          NOT NULL DEFAULT 0,
    bookmark_count   INT          NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_roadmap_step (roadmap_step_id),
    KEY idx_tech_article_user_id (user_id),
    KEY idx_tech_article_skill_status (skill_id, status, published_at),
    KEY idx_tech_article_status_published (status, published_at),
    KEY idx_tech_article_source_status_published (source_type, status, published_at),
    -- 스펙 아카이브 검색(37번). ngram 파서 — 기본 파서는 공백으로 단어를 잘라 한국어 부분 일치가 안 된다.
    -- ngram_token_size가 2라 한 글자 검색은 안 걸리고, 그때는 코드가 LIKE로 떨어진다(TechArticleDao.search).
    FULLTEXT KEY ft_tech_article_text (title, content) WITH PARSER ngram,
    CONSTRAINT fk_tech_article_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_roadmap_step
        FOREIGN KEY (roadmap_step_id) REFERENCES ROADMAP_STEP (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_COMMENT (기술 글 댓글) — 신설
-- 대댓글은 한 단계만 허용한다(parent_comment_id가 가리키는 댓글은 최상위여야 함 — 애플리케이션 규칙).
-- 인스타그램식: 답글에 다시 답해도 같은 최상위 댓글 밑에 모이고, 답한 상대는 reply_to_user_id(@이름)로 남긴다.
-- 삭제는 is_deleted로 하고, 대댓글이 달린 댓글은 화면에 "삭제된 댓글입니다"로 남긴다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_COMMENT (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    article_id          BIGINT         NOT NULL,
    user_id             BIGINT         NOT NULL,
    parent_comment_id   BIGINT         NULL, -- 대댓글이면 부모 댓글 (자기참조)
    reply_to_user_id    BIGINT         NULL, -- 답글이 가리키는 사람 — 화면에 @이름. 최상위 댓글이면 NULL
    content             VARCHAR(1000)  NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_tech_article_comment_article_id (article_id, created_at),
    KEY idx_tech_article_comment_user_id (user_id),
    KEY idx_tech_article_comment_parent_id (parent_comment_id),
    KEY idx_tech_article_comment_reply_to_user_id (reply_to_user_id),
    CONSTRAINT fk_tech_article_comment_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_comment_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES TECH_ARTICLE_COMMENT (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_comment_reply_to_user
        FOREIGN KEY (reply_to_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_ATTACHMENT (글 첨부) — 신설 (스펙 아카이브, sql/19_alter_tech_article_spec_archive.sql)
-- 글 하나에 여러 개(개수 제한 없음, 용량만 — 사진 1장 10MB). 본문의 [[att:N]](N = sort_order)이 놓일 자리다.
-- attachment_type에 따라 쓰는 컬럼이 다르다.
--   IMAGE_UPLOAD : 올린 이미지(글당 합계 10MB) → original_name · file_size · mime_type · file_data(사진 내용, sql/20)
--                  stored_name · file_path는 디스크 저장 시절 행에만 있다 (새 행은 비어 있음)
--   IMAGE_URL    : 외부 이미지 링크(https) → url
--   YOUTUBE      : 유튜브 영상 → url(원본) · embed_key(영상 ID)
-- 복합 UNIQUE: (article_id, sort_order) — 한 글 안에서 표시 순서가 겹치지 않게.
-- =========================================================
CREATE TABLE TECH_ARTICLE_ATTACHMENT (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    article_id       BIGINT         NOT NULL,
    attachment_type  VARCHAR(20)    NOT NULL, -- IMAGE_UPLOAD / IMAGE_URL / YOUTUBE
    sort_order       INT            NOT NULL,
    url              VARCHAR(2048)  NULL,
    embed_key        VARCHAR(20)    NULL,
    original_name    VARCHAR(255)   NULL,
    stored_name      VARCHAR(255)   NULL,
    file_path        VARCHAR(500)   NULL,
    file_size        BIGINT         NULL,
    mime_type        VARCHAR(100)   NULL,
    file_data        MEDIUMBLOB     NULL, -- IMAGE_UPLOAD 사진 내용 — 어느 서버에서든 보이게 DB에 저장
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_attachment_article_order (article_id, sort_order),
    CONSTRAINT fk_tech_article_attachment_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_LIKE (기술 글 하트) — 신설
-- 복합 UNIQUE: (article_id, user_id) — 한 사람이 한 글에 하트는 한 번.
-- 취소는 is_deleted = TRUE, 다시 누르면 같은 행을 되살린다(EVALUATION_SESSION_ITEM과 같은 방식).
-- =========================================================
CREATE TABLE TECH_ARTICLE_LIKE (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    article_id   BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_like_article_user (article_id, user_id),
    KEY idx_tech_article_like_user_id (user_id),
    CONSTRAINT fk_tech_article_like_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_like_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_BOOKMARK (기술 글 북마크) — 신설
-- 복합 UNIQUE: (article_id, user_id). 취소·재등록 방식은 TECH_ARTICLE_LIKE와 같다.
-- 내 북마크 목록은 user_id 인덱스로 읽는다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_BOOKMARK (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    article_id   BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_bookmark_article_user (article_id, user_id),
    KEY idx_tech_article_bookmark_user_id (user_id, created_at),
    CONSTRAINT fk_tech_article_bookmark_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_bookmark_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_VIEW_LOG (기술 글 조회 이력) — 신설
-- 조회수를 새로고침마다 올리지 않으려고 "사용자 1명 × 글 1개 × 하루 1회"만 센다.
-- 복합 UNIQUE: (article_id, viewer_user_id, viewed_date) — 이미 있으면 TECH_ARTICLE.view_count를 올리지 않는다.
-- 작성자 본인의 조회는 세지 않는다(애플리케이션 규칙).
-- =========================================================
CREATE TABLE TECH_ARTICLE_VIEW_LOG (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    article_id       BIGINT   NOT NULL,
    viewer_user_id   BIGINT   NOT NULL,
    viewed_date      DATE     NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_view_log_article_viewer_date (article_id, viewer_user_id, viewed_date),
    KEY idx_tech_article_view_log_viewer_user_id (viewer_user_id),
    CONSTRAINT fk_tech_article_view_log_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_view_log_viewer
        FOREIGN KEY (viewer_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_REPORT (기술 글 신고) — 신설
-- 자동 게시 + 사후 관리(개발일지 4-3)에서 "문제 있는 글을 팀이 나중에 내린다"의 입력 창구.
-- 복합 UNIQUE: (article_id, reporter_user_id) — 한 사람이 같은 글을 여러 번 신고해 건수를 부풀리지 못하게.
-- 처리 결과로 글을 내리면 TECH_ARTICLE.status = HIDDEN, 이 행은 ACTION_TAKEN으로 바꾼다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_REPORT (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    article_id         BIGINT        NOT NULL,
    reporter_user_id   BIGINT        NOT NULL,
    reason_type        VARCHAR(20)   NOT NULL, -- SPAM(광고) / ABUSE(비방) / COPYRIGHT(무단 복제) / INACCURATE(잘못된 내용) / OTHER
    detail             VARCHAR(500)  NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN', -- OPEN(대기) / ACTION_TAKEN(글을 내림) / DISMISSED(문제 없음)
    handled_at         DATETIME      NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_report_article_reporter (article_id, reporter_user_id),
    KEY idx_tech_article_report_status (status, created_at),
    KEY idx_tech_article_report_reporter_user_id (reporter_user_id),
    CONSTRAINT fk_tech_article_report_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_report_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- SCORING_RULE (점수·복습 주기 규칙) — 신설. 기본값 행은 sql/04_seed_extended.sql
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- SIMULATION_STATE (테스트 계정 시뮬레이션 진행 상태) — 신설, sql/26_alter_users_is_test_simulation.sql · 27(target_score)
-- 계정마다 한 행. 목표 점수에 닿을 때까지 하루씩 돈다. 일시정지 후 days_done 다음 날부터 이어 간다.
-- 초기화하면 행도 지운다(테스트 계정만 물리 삭제).
-- ---------------------------------------------------------------------------
CREATE TABLE SIMULATION_STATE (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    status       VARCHAR(10)   NOT NULL,           -- RUNNING / PAUSED / DONE
    persona      VARCHAR(20)   NOT NULL,           -- DILIGENT(성실) / STEADY(보통) / ON_OFF(작심삼일)
    target_score INT           NULL,               -- 목표 점수 — 총점이 이 점수에 닿으면 멈춘다
    start_date   DATE          NOT NULL,
    total_days   INT           NOT NULL,           -- 최대 날 수(365) — 목표에 못 닿아도 여기서 멈춘다
    days_done    INT           NOT NULL DEFAULT 0,
    started_at   DATETIME      NOT NULL,
    last_error   VARCHAR(500)  NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_simulation_state_user (user_id),
    CONSTRAINT fk_simulation_state_user FOREIGN KEY (user_id) REFERENCES USERS (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_EDUCATION (학력) — 신설 (31번)
-- 관련 요구사항: FR-81 이력 · NFR-4 공개 범위(SHARE_LINK.scope_education)
-- UNIQUE: user_id — 계정당 최종 학력 한 줄
-- =========================================================
CREATE TABLE USER_EDUCATION (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    school_name        VARCHAR(100)  NOT NULL,
    graduation_status  VARCHAR(20)   NOT NULL,        -- ENROLLED(재학) / LEAVE(휴학) / EXPECTED(졸업 예정) / GRADUATED(졸업)
    graduation_date    DATE          NULL,            -- 졸업일 또는 졸업 예정일
    gpa                DECIMAL(3,2)  NULL,
    gpa_max            DECIMAL(3,2)  NULL,            -- 4.5 / 4.3 / 4.0 — 학점을 넣으면 같이 넣는다
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_education_user (user_id),
    CONSTRAINT fk_user_education_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- COMPANION_DEVICE (데스크톱 캐릭터 연결) — 신설, sql/32_schema_companion_device.sql
-- 웹 로그인 사용자의 일회용 코드 → 캐릭터 전용 토큰. 코드·토큰은 SHA-256 해시만 저장. 한 행 = 한 PC.
-- ---------------------------------------------------------------------------
CREATE TABLE COMPANION_DEVICE (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    device_name        VARCHAR(100)  NULL,     -- PC 이름 (연결할 때 exe가 알려 줌)
    connect_code_hash  CHAR(64)      NULL,     -- 일회용 코드 해시 — 토큰으로 바꾸면 NULL
    code_expires_at    DATETIME      NULL,     -- 일회용 코드 만료 (발급 1분 뒤)
    token_hash         CHAR(64)      NULL,     -- 캐릭터 전용 토큰 해시 — 연결 전에는 NULL
    connected_at       DATETIME      NULL,
    last_used_at       DATETIME      NULL,
    revoked_at         DATETIME      NULL,     -- 연결 해제 시각
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_device_token (token_hash),
    UNIQUE KEY uk_companion_device_code (connect_code_hash),
    KEY idx_companion_device_user (user_id),
    CONSTRAINT fk_companion_device_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SKILL_PREREQUISITE (기술 선수관계) — 신설 (34번)
-- 관련 요구사항: FR-33 로드맵 단계 이유 · FR-39 로드맵 생성
-- UNIQUE: (skill_id, prereq_skill_id) — 같은 관계가 두 번 들어가면 위상 정렬의 진입차수가 어긋난다
-- "A를 하기 전에 B". 로드맵은 이 그래프를 위상 정렬(Kahn)해 순서를 정하고,
-- 관리자 화면은 추가할 때 DFS로 순환(A→B→A)을 막는다. 초기 데이터는 sql/35.
-- =========================================================
CREATE TABLE SKILL_PREREQUISITE (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    skill_id        BIGINT   NOT NULL, -- 뒤에 와야 하는 기술
    prereq_skill_id BIGINT   NOT NULL, -- 먼저 와야 하는 기술
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_prerequisite (skill_id, prereq_skill_id),
    KEY idx_skill_prerequisite_prereq (prereq_skill_id),
    CONSTRAINT fk_skill_prerequisite_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_skill_prerequisite_prereq
        FOREIGN KEY (prereq_skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- SKILL_REVIEW_SCHEDULE (간격 반복 복습 일정) — 신설 (36번)
-- 관련 요구사항: FR-39 로드맵 복습
-- UNIQUE: (user_id, skill_id) — 두 줄이면 어느 일정이 맞는지 알 수 없다
-- 복습 주기가 티어로만 고정이던 것을 SM-2(Anki 공식)로 기술마다 다르게 잡는다.
-- 행이 없는 기술은 아직 복습한 적이 없고, 그때는 "마지막으로 익힌 날 + 티어 기본 주기"를 쓴다.
-- =========================================================
CREATE TABLE SKILL_REVIEW_SCHEDULE (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL,
    ease_factor      DECIMAL(4,2) NOT NULL DEFAULT 2.50, -- SM-2 EF, 하한 1.30 상한 2.80
    interval_days    INT          NOT NULL,              -- 이번에 적용한 간격
    repetitions      INT          NOT NULL DEFAULT 0,    -- 연속 통과 횟수
    last_quality     TINYINT      NULL,                  -- 마지막 자기 평가 0~5 (SM-2 q)
    last_reviewed_at DATETIME     NULL,
    due_at           DATETIME     NOT NULL,              -- 다음 복습 예정
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_review_schedule (user_id, skill_id),
    KEY idx_skill_review_schedule_due (user_id, due_at),
    CONSTRAINT fk_skill_review_schedule_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_skill_review_schedule_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
-- ---------------------------------------------------------------------------
-- COMPANION_RELEASE · COMPANION_RELEASE_CHUNK (데스크톱 캐릭터 설치 파일) — 신설, sql/38_schema_companion_release.sql
-- Setup.exe를 8MB씩 나눠 DB에 둔다 (누구 서버에서든 같은 파일). 최근 2개 버전만 내용을 남긴다.
-- ---------------------------------------------------------------------------
CREATE TABLE COMPANION_RELEASE (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     VARCHAR(20)   NOT NULL,           -- 예: 0.2.0
    notes       VARCHAR(500)  NULL,               -- 바뀐 점 (캐릭터 업데이트 말풍선에 나온다)
    file_name   VARCHAR(100)  NOT NULL,
    file_size   BIGINT        NOT NULL,
    sha256      CHAR(64)      NOT NULL,           -- 캐릭터가 내려받은 파일을 이 값으로 검사한다
    uploaded_by BIGINT        NULL,               -- 올린 관리자
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_release_version (version),
    KEY idx_companion_release_uploaded_by (uploaded_by),
    CONSTRAINT fk_companion_release_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES USERS (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE COMPANION_RELEASE_CHUNK (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    release_id  BIGINT        NOT NULL,
    seq         INT           NOT NULL,           -- 0부터 순서대로 이어 붙인다
    data        MEDIUMBLOB    NULL,               -- 최대 8MB. 오래된 버전은 NULL로 비운다
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_release_chunk_seq (release_id, seq),
    CONSTRAINT fk_companion_release_chunk_release
        FOREIGN KEY (release_id) REFERENCES COMPANION_RELEASE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
