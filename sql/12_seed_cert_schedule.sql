-- =========================================================
-- CERT_SCHEDULE 예시 시드 (2026-10-01, FR-71 D-day 자동 생성 연결용)
-- ⚠️ 공식 API/수집 파이프라인이 아직 없어 수기로 넣은 예시 일정이다 — 실제 접수·시험 일자와
-- 다를 수 있다. 정식 수집 담당자가 붙으면(큐넷 등 공식 출처 확인 후) 이 파일은 지우고 실데이터로
-- 교체할 것. 지금은 "시험 일정이 들어오면 D-day가 자동 생성된다"는 기능 자체를 보여주기 위한
-- 최소 데이터다 — CERTIFICATION 시드에 이미 있는 이름과 매칭한다(sql/02_seed.sql).
-- =========================================================

SET NAMES utf8mb4;

INSERT INTO CERT_SCHEDULE (certification_id, round_name, apply_start, apply_end, exam_date)
SELECT c.id, x.round_name, x.apply_start, x.apply_end, x.exam_date
FROM (
    SELECT '정보처리기사' AS cert_name, '2026년 4회' AS round_name,
           '2026-10-06' AS apply_start, '2026-10-10' AS apply_end, '2026-11-08' AS exam_date
    UNION ALL
    SELECT '정보처리산업기사', '2026년 4회',
           '2026-10-06', '2026-10-10', '2026-11-08'
    UNION ALL
    SELECT 'SQLD', '2026년 50회',
           '2026-10-13', '2026-10-17', '2026-11-15'
) AS x
JOIN CERTIFICATION c ON c.cert_name = x.cert_name;
