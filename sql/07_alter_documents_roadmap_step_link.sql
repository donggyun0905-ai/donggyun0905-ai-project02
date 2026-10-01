-- =========================================================
-- 공부노트·기술 설명 글(PDF)을 DOCUMENTS로 저장하도록 변경 — 2026-09-30 팀 결정
-- (06_alter_skill_learning_proof.sql 이후 추가 결정: proof_content TEXT 대신
--  실제 PDF 파일을 DOCUMENTS에 저장하고 ROADMAP_STEP과 연결한다.)
-- 관련 요구사항: FR-32 · 33 · 36
-- =========================================================

ALTER TABLE DOCUMENTS
    ADD COLUMN roadmap_step_id BIGINT NULL AFTER project_id,
    ADD KEY idx_documents_roadmap_step_id (roadmap_step_id),
    ADD CONSTRAINT fk_documents_roadmap_step
        FOREIGN KEY (roadmap_step_id) REFERENCES ROADMAP_STEP (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;
