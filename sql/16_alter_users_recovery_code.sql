-- 스펙 오디세이 (Spec Odyssey) — 비밀번호 찾기용 복구 코드: USERS.recovery_code_hash
-- 대상: sql/01_schema.sql로 이미 테이블을 만든 DB에만 실행한다. (01에는 이미 반영되어 있어 새 DB는 실행할 필요 없다.)
-- 기준 문서: docs/db-design.md "USERS"
-- 복구 코드 원문은 저장하지 않는다 — 가입(또는 발급) 때 화면에 한 번만 보여 주고, 비밀번호와 같은 방식(PBKDF2)의 해시만 둔다.
-- 이 값이 NULL이면 복구 코드가 없는 계정(기존 가입자)이라 비밀번호 찾기를 쓸 수 없고, 로그인한 뒤 프로필에서 발급받는다.

ALTER TABLE USERS
    ADD COLUMN recovery_code_hash VARCHAR(255) NULL AFTER privacy_consent_at;
