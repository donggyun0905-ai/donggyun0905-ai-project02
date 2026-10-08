-- 스펙 오디세이 (Spec Odyssey) — 간격 반복 복습 일정 SKILL_REVIEW_SCHEDULE (2026-10-08)
-- 대상: 35번까지 실행한 DB. 한 번만 실행한다.
--       (03_schema_extended.sql에도 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "SKILL_REVIEW_SCHEDULE"
--
-- 복습 주기는 지금까지 티어로만 고정이었다(입문 30일 · 핵심 60 · 심화 90 · 전문가 120).
-- 같은 사람이 쉬운 기술과 어려운 기술을 같은 주기로 복습했다. 이제 복습을 낼 때 "얼마나 기억났는지"를
-- 받아 SM-2(Anki가 쓰는 간격 반복 공식)로 기술마다 다음 복습일을 따로 계산한다.
--
--   ease_factor  : 기억 난이도. 쉬우면 올라가고 어려우면 내려간다. SM-2 권장 시작값 2.5, 하한 1.3
--   interval_days: 이번에 적용한 간격. 다음 간격은 이 값 × ease_factor
--   repetitions  : 연속으로 통과한 횟수. "거의 잊었다"가 나오면 0으로 돌아간다
--   due_at       : 다음 복습 예정 시각. RoadmapReviewService가 이 값으로 복습 단계를 만든다
--
-- 한 행 = (사람, 기술) 하나. 복습을 한 번도 안 한 기술은 행이 없고, 그때는 예전처럼 티어 기본 주기를 쓴다.

SET NAMES utf8mb4;

CREATE TABLE SKILL_REVIEW_SCHEDULE (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    skill_id      BIGINT        NOT NULL,
    ease_factor   DECIMAL(4,2)  NOT NULL DEFAULT 2.50 COMMENT 'SM-2 EF — 하한 1.30',
    interval_days INT           NOT NULL COMMENT '이번에 적용한 간격(일)',
    repetitions   INT           NOT NULL DEFAULT 0 COMMENT '연속 통과 횟수',
    last_quality  TINYINT       NULL     COMMENT '마지막 자기 평가 0~5 (SM-2 q)',
    last_reviewed_at DATETIME   NULL,
    due_at        DATETIME      NOT NULL COMMENT '다음 복습 예정',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    -- 사람·기술당 한 줄 — 두 줄이면 어느 일정이 맞는지 알 수 없다
    UNIQUE KEY uk_skill_review_schedule (user_id, skill_id),
    KEY idx_skill_review_schedule_due (user_id, due_at),
    CONSTRAINT fk_skill_review_schedule_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_skill_review_schedule_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
