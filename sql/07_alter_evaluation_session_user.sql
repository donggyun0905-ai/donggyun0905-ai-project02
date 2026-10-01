-- 스펙 오디세이 (Spec Odyssey) — EVALUATION_SESSION에 면접관 계정 연결 추가
-- 대상: sql/03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03_schema_extended.sql에는 이 컬럼이 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "EVALUATION_SESSION"
--
-- 면접관 계정(USERS.user_type = 'INTERVIEWER')이 생기면서, 비교 목록의 주인을 브라우저 세션 토큰이 아니라
-- 계정으로 식별한다. user_id가 NULL인 행은 계정 없는 익명 세션(기존 방식)이다.
-- UNIQUE(user_id): 면접관 한 명에 비교 목록 하나. NULL은 여러 개 허용되므로 익명 세션에는 영향이 없다.

ALTER TABLE EVALUATION_SESSION
    ADD COLUMN user_id BIGINT NULL AFTER id,
    ADD UNIQUE KEY uk_evaluation_session_user (user_id),
    ADD CONSTRAINT fk_evaluation_session_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;
