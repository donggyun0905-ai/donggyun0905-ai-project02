-- 시뮬레이션을 "70일 고정"에서 "목표 점수에 닿을 때까지"로 바꾼다
-- 대상: 24번까지 실행한 DB. 한 번만 실행한다. (03_schema_extended.sql에도 반영되어 있어 새 DB에서는 필요 없다)
--
-- target_score : 이 점수(USER_SCORE_SUMMARY.total_score)에 닿으면 멈춘다
-- total_days   : 이제는 "최대 날 수"(365) — 목표에 끝내 닿지 못해도 끝없이 돌지 않게

SET NAMES utf8mb4;

ALTER TABLE SIMULATION_STATE
    ADD COLUMN target_score INT NULL COMMENT '목표 점수 — 총점이 이 점수에 닿으면 멈춘다' AFTER persona;
