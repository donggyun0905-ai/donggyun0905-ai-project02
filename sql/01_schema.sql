-- 스펙 오디세이 (Spec Odyssey) — 1주차 스키마
-- 대상: 회원/인증 + 프로필 CRUD 관련 8개 테이블
-- 기준 문서: docs/db-design.md
-- 문자셋: utf8mb4 / utf8mb4_unicode_ci
-- 공통 컬럼(created_at, updated_at, is_deleted)은 모든 테이블에 포함. 물리 삭제 금지.
-- FK 삭제 정책: 전부 ON DELETE RESTRICT ON UPDATE CASCADE로 통일.
--   CASCADE(물리 연쇄 삭제)는 논리 삭제(is_deleted) 방침과 충돌하므로 쓰지 않는다.

SET NAMES utf8mb4;

-- =========================================================
-- SKILL (기술 마스터) — 신설
-- 관련 요구사항: TD-1 임베딩 시맨틱 매칭
-- =========================================================
CREATE TABLE SKILL (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    skill_name        VARCHAR(100) NOT NULL,
    category          VARCHAR(50)  NULL,
    embedding_vector  TEXT         NULL,
    embedding_model   VARCHAR(50)  NULL,
    embedded_at       DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_name (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB (직무 마스터) — 신설
-- 관련 요구사항: TD-2 On-demand 수집
-- =========================================================
CREATE TABLE JOB (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    job_name            VARCHAR(100) NOT NULL,
    job_category        VARCHAR(50)  NULL,
    is_popular          BOOLEAN      NOT NULL DEFAULT FALSE,
    last_collected_at   DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_name (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- CERTIFICATION (자격증 마스터) — 신설
-- 관련 요구사항: FR-71 자동화
-- =========================================================
CREATE TABLE CERTIFICATION (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    cert_name         VARCHAR(100) NOT NULL,
    issuer            VARCHAR(100) NULL,
    job_category      VARCHAR(50)  NULL,
    difficulty_level  INT          NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cert_name (cert_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USERS (회원)
-- 관련 요구사항: FR-11 · 12 · 13 · 14 · 21 · 22
-- =========================================================
CREATE TABLE USERS (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_type             VARCHAR(15)  NOT NULL DEFAULT 'APPLICANT', -- APPLICANT(지원자) / INTERVIEWER(면접관, 미사용) — TD-4
    login_id              VARCHAR(50)  NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    email                 VARCHAR(100) NULL,
    major                 VARCHAR(50)  NULL,
    grade                 VARCHAR(20)  NULL,
    interest_field        VARCHAR(50)  NULL,
    desired_job_id        BIGINT       NULL,
    desired_job_status    VARCHAR(10)  NOT NULL DEFAULT 'UNSET',
    privacy_consent_at    DATETIME     NULL,
    profile_updated_at    DATETIME     NULL,
    last_login_at         DATETIME     NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login_id (login_id),
    KEY idx_users_email (email),
    KEY idx_users_desired_job_id (desired_job_id),
    CONSTRAINT fk_users_desired_job
        FOREIGN KEY (desired_job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_SPECS (보유 스펙)
-- 관련 요구사항: FR-23 · 81(면접관 타임라인)
-- =========================================================
CREATE TABLE USER_SPECS (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    spec_type       VARCHAR(20)  NOT NULL,
    title           VARCHAR(100) NOT NULL,
    issuer          VARCHAR(100) NULL,
    score           VARCHAR(20)  NULL,
    acquired_date   DATE         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_user_specs_user_id (user_id),
    CONSTRAINT fk_user_specs_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_PROJECTS (프로젝트·경험)
-- 관련 요구사항: FR-24
-- =========================================================
CREATE TABLE USER_PROJECTS (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(150) NOT NULL,
    description   TEXT         NULL,
    tech_stack    VARCHAR(255) NULL,
    start_date    DATE         NULL,
    end_date      DATE         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_user_projects_user_id (user_id),
    CONSTRAINT fk_user_projects_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- USER_SKILLS (보유 기술 스택)
-- 관련 요구사항: FR-25 (필수로 격상)
-- 복합 UNIQUE: (user_id, skill_id) — 같은 기술을 두 번 입력해도 한 줄만 남는다
-- =========================================================
CREATE TABLE USER_SKILLS (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    skill_id           BIGINT        NULL,
    raw_input          VARCHAR(100)  NOT NULL,
    similarity_score   DECIMAL(5,4)  NULL,
    proficiency        VARCHAR(20)   NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_skills_user_skill (user_id, skill_id),
    KEY idx_user_skills_user_id (user_id),
    KEY idx_user_skills_skill_id (skill_id),
    CONSTRAINT fk_user_skills_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_user_skills_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- JOB_ALIAS (직무명 별칭) — 신설
-- 관련 요구사항: TD-2 On-demand 수집
-- 복합 UNIQUE: (alias_name) — 같은 표기가 두 직무를 가리키지 않게
-- =========================================================
CREATE TABLE JOB_ALIAS (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    job_id             BIGINT        NOT NULL,
    alias_name         VARCHAR(100)  NOT NULL,
    match_type         VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    similarity_score   DECIMAL(5,4)  NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_alias_alias_name (alias_name),
    KEY idx_job_alias_job_id (job_id),
    CONSTRAINT fk_job_alias_job
        FOREIGN KEY (job_id) REFERENCES JOB (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
