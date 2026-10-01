-- 스펙 오디세이 (Spec Odyssey) — USERS에 이력서 파일 연결 추가
-- 대상: sql/01_schema.sql · 03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (두 파일에는 이 컬럼과 FK가 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "USERS"
--
-- 이력서는 파일로 저장한다. 파일 자체는 서류 보관함(DOCUMENTS)에 올리고, 그중 어느 파일이
-- "내 이력서"인지를 이 컬럼이 가리킨다. 이력서를 지정하지 않았으면 NULL.

ALTER TABLE USERS
    ADD COLUMN resume_document_id BIGINT NULL AFTER desired_job_status,
    ADD KEY idx_users_resume_document_id (resume_document_id),
    ADD CONSTRAINT fk_users_resume_document
        FOREIGN KEY (resume_document_id) REFERENCES DOCUMENTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;
