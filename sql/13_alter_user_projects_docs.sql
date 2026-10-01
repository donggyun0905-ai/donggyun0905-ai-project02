-- 스펙 오디세이 (Spec Odyssey) — 프로젝트 완료 시 받는 것: 저장소·배포 링크·회고 + 기술 활용 설명서 + 문서 체크리스트
-- 대상: sql/01_schema.sql · 03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (두 파일에는 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "USER_PROJECTS", "PROJECT_TECH_NOTE", "PROJECT_DOCUMENT_ITEM" (개발일지 4-4, 2026-09-30 확정)

ALTER TABLE USER_PROJECTS
    ADD COLUMN repo_url       VARCHAR(500) NULL AFTER upgraded_from_project_id,
    ADD COLUMN deploy_url     VARCHAR(500) NULL AFTER repo_url,
    ADD COLUMN retrospective  TEXT         NULL AFTER deploy_url;

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
