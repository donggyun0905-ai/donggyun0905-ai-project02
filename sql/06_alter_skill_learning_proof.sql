-- =========================================================
-- SKILL 단계 학습 검증(규칙 기반) 추가 — 2026-09-30 팀 결정
-- 관련 요구사항: FR-32 · 33 · 36
-- 이미 만들어진(공유) DB에 01_schema.sql/03_schema_extended.sql을 다시 실행할 수 없어
-- ALTER로 반영한다. 새로 DB를 만드는 사람은 01_schema.sql/03_schema_extended.sql만
-- 실행하면 되고(이미 반영됨), 이 파일은 기존 DB에 한 번만 실행하면 된다.
-- =========================================================

ALTER TABLE USER_PROJECTS
    ADD COLUMN upgraded_from_project_id BIGINT NULL AFTER end_date,
    ADD KEY idx_user_projects_upgraded_from (upgraded_from_project_id),
    ADD CONSTRAINT fk_user_projects_upgraded_from
        FOREIGN KEY (upgraded_from_project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE ROADMAP_STEP
    ADD COLUMN proof_type VARCHAR(20) NULL AFTER reason,
    ADD COLUMN proof_content TEXT NULL AFTER proof_type,
    ADD COLUMN evidence_project_id BIGINT NULL AFTER proof_content,
    ADD COLUMN review_status VARCHAR(20) NULL AFTER evidence_project_id,
    ADD COLUMN review_note TEXT NULL AFTER review_status,
    ADD KEY idx_roadmap_step_evidence_project_id (evidence_project_id),
    ADD CONSTRAINT fk_roadmap_step_evidence_project
        FOREIGN KEY (evidence_project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;
