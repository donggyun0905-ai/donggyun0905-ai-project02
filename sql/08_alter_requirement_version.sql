-- =========================================================
-- 로드맵이 한 번 만들면 고정되는 문제 — 변화 감지용 버전 컬럼 (2026-09-30 팀 결정)
-- 관련 요구사항: FR-32 · 33 · 36 · 37
-- =========================================================

ALTER TABLE JOB
    ADD COLUMN requirement_version INT NOT NULL DEFAULT 1 AFTER last_collected_at;

ALTER TABLE GAP_ANALYSIS
    ADD COLUMN job_requirement_version INT NULL AFTER match_rate;
