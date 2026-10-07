-- 스펙 오디세이 (Spec Odyssey) — SHARE_LINK에 활동 내역 공개 범위 추가
-- 대상: sql/03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03_schema_extended.sql에는 이 컬럼이 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "SHARE_LINK"
--
-- 면접관의 "활동 내역"(날짜별 활동량 잔디 + 언제 무엇을 했는지 타임라인)에 쓴다.
-- 활동 기록은 "며칠에 몇 시에 무엇을 했는지"까지 드러나 기존 공개 항목보다 민감하다 — scope_basic에
-- 묶지 않고 지원자가 링크마다 따로 고르게 했다(scope_age와 같은 판단, NFR-4 본인 선택 공유).
-- 기본값 false라 이 컬럼이 생기기 전에 만든 링크는 모두 비공개로 남는다.

SET NAMES utf8mb4;

ALTER TABLE SHARE_LINK
    ADD COLUMN scope_activity BOOLEAN NOT NULL DEFAULT FALSE COMMENT '활동 내역(잔디·타임라인) 공개 — 지원자가 링크마다 고른다' AFTER scope_age;
