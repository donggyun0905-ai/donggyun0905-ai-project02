-- 스펙 오디세이 (Spec Odyssey) — USERS에 자소서 파일 연결, SHARE_LINK에 자소서 공개 범위 추가
-- 대상: sql/01_schema.sql · 03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (두 파일에는 이 컬럼과 FK가 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 선행: sql/09_alter_users_resume.sql, sql/10_alter_share_link_scope_resume.sql (이력서 컬럼과 같은 방식이다)
-- 기준 문서: docs/db-design.md "USERS", "SHARE_LINK"
--
-- 내 프로필에서 이력서·자소서를 선택으로 올리고, 공유 링크마다 "자소서 파일"을 따로 열어줄 수 있게 한다.
-- 파일 자체는 서류 보관함(DOCUMENTS)에 올리고, 이 컬럼이 "내 자소서"가 어느 파일인지 가리킨다.
-- 자소서에는 개인 이야기가 많이 들어 있어 공유 범위 기본값은 비공개(FALSE)다 — 이력서와 같다(NFR-4).

ALTER TABLE USERS
    ADD COLUMN cover_letter_document_id BIGINT NULL AFTER resume_document_id,
    ADD KEY idx_users_cover_letter_document_id (cover_letter_document_id),
    ADD CONSTRAINT fk_users_cover_letter_document
        FOREIGN KEY (cover_letter_document_id) REFERENCES DOCUMENTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE SHARE_LINK
    ADD COLUMN scope_cover_letter BOOLEAN NOT NULL DEFAULT FALSE AFTER scope_resume;
