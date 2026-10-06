-- 스펙 오디세이 (Spec Odyssey) — 회원 탈퇴 유예 기간: USERS.withdraw_requested_at
-- 대상: sql/01_schema.sql로 이미 테이블을 만든 DB에만 실행한다. (01에는 이미 반영되어 있어 새 DB는 실행할 필요 없다.)
-- 기준 문서: docs/db-design.md "USERS" (팀 확인 2026-10-03)
--
-- 탈퇴 신청 시각. 탈퇴하면 is_deleted = TRUE + 이 시각을 남기고, 30일 동안은 아이디를 바꾸지 않고 잡아 둔다
-- (그 사이 같은 아이디로 다른 사람이 가입할 수 없다). 30일 안에 다시 로그인하면 탈퇴를 취소할 수 있다.
-- 30일이 지나면 WithdrawalPurgeScheduler가 개인정보를 지우고 아이디를 del_<id>_로 비운다.
-- NULL이면서 is_deleted = TRUE인 행은 이 컬럼이 생기기 전에 탈퇴한 계정이다(유예 없음, 복구 불가).

ALTER TABLE USERS
    ADD COLUMN withdraw_requested_at DATETIME NULL AFTER last_login_at;
