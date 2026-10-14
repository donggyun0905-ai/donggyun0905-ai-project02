-- =========================================================
-- JOB_REQUIRED_SKILL.required_level 을 로드맵 티어와 같은 4단계 어휘로 통일 (2026-10-14)
-- 관련 요구사항: FR-34 격차 분석 · FR-39 로드맵 · FR-48 약점 히트맵
--
-- 왜
--   같은 서비스 안에 "기술을 어디까지 해야 하는가"를 재는 자가 둘이었다.
--     로드맵 티어      ENTRY · CORE · ADVANCED · EXPERT   (입문·핵심·심화·전문가)
--     직무 요구 수준    BASIC · INTERMEDIATE · ADVANCED    (3단계)
--   둘 다 ADVANCED 를 쓰는데 뜻이 달라서, 히트맵의 "심화"와 로드맵의 "심화"가
--   같은 것으로 읽혔다. 요구 수준을 티어와 같은 말로 바꾸면 "직무가 바라는 깊이"와
--   "내가 증명한 깊이"를 같은 자로 잴 수 있다.
--
-- 어떻게 — 손으로 정하지 않았다. 원래 있던 두 칸(importance + required_level)을 합쳤다.
--     우대(PREFERRED)            → ENTRY     읽고 정리하면 된다        (103건)
--     필수 + BASIC / 값 없음       → CORE      프로젝트에 써 봐야 한다     ( 27건)
--     필수 + INTERMEDIATE        → ADVANCED  깊이 다뤄 봐야 한다       ( 57건)
--     필수 + ADVANCED            → EXPERT    남에게 설명할 수 있어야 한다 ( 17건)
--
--   네 칸이 모두 찬다. 각 티어의 뜻은 제출물로 정의돼 있다 —
--   입문=공부노트 PDF, 핵심=프로젝트 등록, 심화=기존 프로젝트 업그레이드, 전문가=기술 설명 글.
--
-- 주의: 한 번만 실행한다. 옛 어휘(BASIC/INTERMEDIATE)가 남아 있을 때만 돌도록
--       아래 @needs_convert 로 막아 뒀다. ADVANCED 가 옛 값이자 새 값이라,
--       두 번 돌면 심화가 전문가로 한 칸씩 밀려 올라간다.
-- =========================================================

-- ---------------------------------------------------------
-- 0) 부하 테스트 잔재 정리 — 운영 DB 에 '초대량스킬…' 이름의 기술 14개가 남아 있었다.
--    'API 개발자' 직무에 요구 기술로 붙어 화면에 그대로 나왔다(수준도 비어 있어 "미지정" 열을 만들었다).
--    보유한 사용자 0명 · 로드맵 단계 0건 · 격차 분석 항목 0건이라 끊어내도 번지지 않는다.
--    물리 삭제는 하지 않는다(프로젝트 규칙 — 논리 삭제만).
-- ---------------------------------------------------------
UPDATE JOB_REQUIRED_SKILL r
    JOIN SKILL s ON s.id = r.skill_id
SET r.is_deleted = TRUE
WHERE s.skill_name LIKE '초대량스킬%'
  AND r.is_deleted = FALSE;

UPDATE SKILL_ALIAS a
    JOIN SKILL s ON s.id = a.skill_id
SET a.is_deleted = TRUE
WHERE s.skill_name LIKE '초대량스킬%'
  AND a.is_deleted = FALSE;

UPDATE SKILL
SET is_deleted = TRUE
WHERE skill_name LIKE '초대량스킬%'
  AND is_deleted = FALSE;

-- ---------------------------------------------------------
-- 1) 두 번 실행 방지 — 옛 어휘가 하나도 없으면 아래 변환을 건너뛴다
-- ---------------------------------------------------------
SET @needs_convert := (
    SELECT COUNT(*) FROM JOB_REQUIRED_SKILL
    WHERE is_deleted = FALSE
      AND required_level IN ('BASIC', 'INTERMEDIATE')
);

-- ---------------------------------------------------------
-- 2) 본 변환 — 한 번의 UPDATE 로 끝낸다.
--    나눠서 돌리면 안 된다: INTERMEDIATE → ADVANCED 로 바꾼 행이
--    바로 다음 문장의 "ADVANCED → EXPERT" 에 다시 걸린다.
-- ---------------------------------------------------------
UPDATE JOB_REQUIRED_SKILL
SET required_level = CASE
        WHEN importance = 'PREFERRED'                              THEN 'ENTRY'
        WHEN required_level IS NULL OR required_level = 'BASIC'     THEN 'CORE'
        WHEN required_level = 'INTERMEDIATE'                        THEN 'ADVANCED'
        WHEN required_level = 'ADVANCED'                            THEN 'EXPERT'
        ELSE required_level
    END
WHERE is_deleted = FALSE
  AND @needs_convert > 0;

-- ---------------------------------------------------------
-- 3) 전문가가 하나도 없는 직무 6개 — 정체성 기술 하나씩 올린다.
--    위 규칙만으로는 ADVANCED 가 원래 없던 직무에 전문가 칸이 비어서,
--    "이 직무를 한다고 말하려면 남에게 설명할 수 있어야 하는 기술"을 하나씩 골랐다.
--    보안 쪽 셋은 서로 겹치지 않게 뒀다 —
--      모의해킹 전문가 Metasploit·Burp Suite / 보안 엔지니어 SIEM / 정보보안 담당자 IAM
--    이미 EXPERT 면 그대로라 다시 실행해도 안전하다.
-- ---------------------------------------------------------
UPDATE JOB_REQUIRED_SKILL r
    JOIN JOB j  ON j.id = r.job_id
    JOIN SKILL s ON s.id = r.skill_id
SET r.required_level = 'EXPERT'
WHERE r.is_deleted = FALSE
  AND (
        (j.job_name = '백엔드 개발자'   AND s.skill_name = 'Spring Boot')    -- 한국 백엔드의 사실상 표준. Java 보다 변별력이 있다
     OR (j.job_name = '서버 개발자'    AND s.skill_name = 'Linux')          -- 서버를 다루는 게 업 — 백엔드 개발자와 구분된다
     OR (j.job_name = '보안 엔지니어'   AND s.skill_name = 'SIEM')           -- 관제·운영이 업
     OR (j.job_name = '정보보안 담당자' AND s.skill_name = 'IAM')            -- 계정·권한 거버넌스
     OR (j.job_name = 'IT 기획자'     AND s.skill_name = 'Agile/Scrum')    -- 방법론이 기획자의 언어
     OR (j.job_name = '서비스 기획자'   AND s.skill_name = 'UI/UX Design')   -- 도구(Figma)가 아니라 역량
  );

-- ---------------------------------------------------------
-- 확인용 (주석 — 필요하면 직접 돌려 본다)
--
--   SELECT required_level, COUNT(*) FROM JOB_REQUIRED_SKILL
--    WHERE is_deleted = FALSE GROUP BY required_level;
--     → ENTRY 103 / CORE 27 / ADVANCED 51 / EXPERT 23   (미지정 0)
--
--   SELECT j.job_name, SUM(r.required_level = 'EXPERT') FROM JOB_REQUIRED_SKILL r
--     JOIN JOB j ON j.id = r.job_id WHERE r.is_deleted = FALSE GROUP BY j.job_name;
--     → 18개 직무 모두 1개 이상
-- ---------------------------------------------------------
