-- 스펙 오디세이 (Spec Odyssey) — 빠져 있던 자격증 보충 (2026-10-03)
-- 대상: CERTIFICATION. 02_seed.sql은 수정 금지라 별도 파일로 둔다. 여러 번 실행해도 안전하다(같은 이름이 있으면 건너뜀).
--
-- 1) 공통(COMMON): 직무와 상관없이 많은 기업이 서류에서 보는 어학 시험. 기존에는 컴활 1·2급뿐이었다.
--    로드맵 입문 티어에 직무 자격증과 별도로 공통 자격증 한 칸이 들어간다(RoadmapGenerator.findSuggestedCommonCertification).
-- 2) FRONTEND: 프론트엔드 계열(UI 개발자·웹퍼블리셔·프론트엔드 개발자)에 연결된 자격증이 하나도 없었다.
-- difficulty_level은 기존 시드와 같은 1(쉬움)~5(어려움) 기준이다.

SET NAMES utf8mb4;

INSERT INTO CERTIFICATION (cert_name, issuer, job_category, difficulty_level)
SELECT s.cert_name, s.issuer, s.job_category, s.difficulty_level
FROM (
    SELECT 'TOEIC' AS cert_name, 'ETS (국내 시행: YBM)' AS issuer, 'COMMON' AS job_category, 2 AS difficulty_level
    UNION ALL SELECT 'TOEIC Speaking', 'ETS (국내 시행: YBM)', 'COMMON', 2
    UNION ALL SELECT 'OPIc', 'ACTFL (국내 시행: 멀티캠퍼스)', 'COMMON', 2
    UNION ALL SELECT '웹디자인개발기능사', '한국산업인력공단', 'FRONTEND', 1
) s
WHERE NOT EXISTS (SELECT 1 FROM CERTIFICATION c WHERE c.cert_name = s.cert_name);
