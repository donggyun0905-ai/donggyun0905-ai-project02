-- =========================================================
-- SKILL_ALIAS (기술 별칭) — 신설, 2026-09-30 팀 결정
-- 관련 요구사항: TD-1 임베딩 시맨틱 매칭 (임베딩 전 중간 단계 — "이름 일치라도")
-- JOB_ALIAS와 같은 패턴: 표준 명칭(SKILL.skill_name) 대신 흔히 쓰는 한글 표기·줄임말을
-- 미리 등록해둔 사전. FuzzyNameMatcher가 정확 일치 다음 순서로 참고한다.
-- =========================================================
CREATE TABLE SKILL_ALIAS (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    skill_id           BIGINT        NOT NULL,
    alias_name         VARCHAR(100)  NOT NULL,
    match_type         VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    similarity_score   DECIMAL(5,4)  NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_alias_alias_name (alias_name),
    KEY idx_skill_alias_skill_id (skill_id),
    CONSTRAINT fk_skill_alias_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
