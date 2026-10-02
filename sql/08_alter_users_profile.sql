-- 스펙 오디세이 (Spec Odyssey) — USERS에 이름·나이·구분 추가
-- 대상: sql/01_schema.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (01_schema.sql에는 이 컬럼들이 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "USERS"
--
-- 회원가입에서 이름·나이·구분(학생/취준생/직장인)을 필수로 받는다. 이름은 면접관이 공유받은 이력과
-- 비교 화면에서 지원자를 구분하는 데 쓴다(공유 링크의 "기본 이력" 범위).
-- 이 컬럼이 생기기 전에 가입한 회원은 값이 없으므로 NULL을 허용한다 — 내 프로필에서 채우게 한다.

ALTER TABLE USERS
    ADD COLUMN name          VARCHAR(50) NULL AFTER password_hash,
    ADD COLUMN age           INT         NULL AFTER name,
    ADD COLUMN career_status VARCHAR(15) NULL AFTER age; -- STUDENT(학생) / JOB_SEEKER(취준생) / EMPLOYED(직장인)
