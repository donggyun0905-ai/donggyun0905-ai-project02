-- 스펙 오디세이 (Spec Odyssey) — 탈퇴한 계정의 아이디 비우기
-- 대상: 이미 운영 중인 DB. 새 DB는 실행하지 않아도 된다(앱이 탈퇴 때 알아서 비운다).
-- 문제: login_id가 UNIQUE인데 탈퇴(논리 삭제)한 계정이 아이디를 그대로 갖고 있어서, 같은 아이디로 다시 가입하면 "이미 사용 중"이 떴다.
-- 이 파일은 이미 탈퇴한 계정의 아이디 앞에 del_<id>_를 붙여 아이디를 다시 쓸 수 있게 한다. 여러 번 실행해도 안전하다.
-- ⚠️ 21_alter_users_withdraw_requested_at.sql을 적용한 뒤에는 실행하지 말 것 — 30일 탈퇴 유예 중인 계정의 아이디까지
--    비워 버려 탈퇴 취소가 안 된다. 그 뒤로는 앱의 WithdrawalPurgeScheduler가 유예가 끝난 계정만 골라 비운다.

UPDATE USERS
SET login_id = LEFT(CONCAT('del_', id, '_', login_id), 50)
WHERE is_deleted = TRUE
  AND login_id NOT LIKE 'del\_%';
