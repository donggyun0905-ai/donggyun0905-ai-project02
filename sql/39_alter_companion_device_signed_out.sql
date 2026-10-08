-- 데스크톱 캐릭터 — 웹에서 로그아웃하면 캐릭터가 "쉬는 상태"가 되는 표시 (2026-10-08)
-- 대상: 38번까지 실행한 DB. 한 번만 실행한다. (03_schema_extended.sql에도 반영되어 있어 새 DB에서는 필요 없다)
--
-- 캐릭터를 켜 둔 PC의 브라우저에서
--   로그아웃          → signed_out_at = 그 시각. 캐릭터는 할 일을 말하지 않고 "로그인하면 자동으로 연결돼요"로 쉰다.
--   다른 계정 로그인  → 같은 행의 user_id를 그 계정으로 바꾸고 signed_out_at = NULL. "캐릭터 연결"을 다시 누를 필요가 없다.
-- 어느 PC의 캐릭터인지는 연결한 브라우저에 남긴 쿠키로 안다 (CompanionAuthService.browserLink — 토큰 해시로 서명).
-- revoked_at(사용자가 직접 연결 해제)과 다르다: 해제한 연결은 로그인해도 되살리지 않는다.

SET NAMES utf8mb4;

ALTER TABLE COMPANION_DEVICE
    ADD COLUMN signed_out_at DATETIME NULL COMMENT '웹 로그아웃으로 쉬는 중 — 다시 로그인하면 NULL' AFTER revoked_at;
