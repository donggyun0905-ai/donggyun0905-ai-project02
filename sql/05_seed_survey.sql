-- =============================================================
-- 직무 발굴 설문 문항 시드 (FR-38)  — 담당: C. 직무 발굴 (강다인)
-- 대상 테이블: SURVEY_QUESTION
--   survey_type       = 'JOB_DISCOVERY'
--   job_category_hint = JOB.job_category 값과 똑같이 맞춤
--                       (BACKEND / FRONTEND / DATA / DEVOPS / SECURITY / PM)
--   score_weight      = 자가진단 전용 컬럼이라 NULL
-- 응답은 USER_SURVEY_ANSWER.answer_value 에 1~5 (전혀 아니다 ~ 매우 그렇다)
--
-- 설계 원칙
--   1) 직무 계열 6개 × 3문항 = 18문항(2026-10-03 2→3문항). 한 문항이 두 계열을 동시에 가리키지 않게 함
--      (기존 3번 "서버·시스템 안정성"은 백엔드/DevOps 가 섞여 있어서 둘로 쪼갬)
--   2) 기술 용어를 몰라도 답할 수 있게 "하는 일"로 물어봄 (희망 직무가 없는 사람이 대상)
--   3) 같은 계열 문항이 붙어 나오지 않게 순서를 섞음 (응답 쏠림 방지)
--   4) 화면설계서 7번의 기존 5문항 중 4개는 문장 그대로 유지 (1, 2, 4, 5번)
-- 실행 순서: 01_schema.sql → 02_seed.sql → 03_schema_extended.sql → 04_seed_skills.sql → 이 파일
-- =============================================================

SET NAMES utf8mb4;

-- 여러 번 실행해도 안전하다(2026-10-03) — 이 파일이 공유 DB에 두 번 실행돼 같은 문항이 24개로
-- 늘어난 적이 있어서, 같은 문구의 살아 있는 문항이 이미 있으면 건너뛰도록 바꿨다.
-- 13~18번은 2026-10-03 추가(계열당 2문항 → 3문항). 2문항이면 계열 점수가 거칠어 동점이 잦았다.
-- 순서는 id 순으로 보이므로, 추가분도 같은 계열이 붙지 않게 섞어 뒤에 붙였다.
INSERT INTO SURVEY_QUESTION (survey_type, content, job_category_hint, score_weight)
SELECT 'JOB_DISCOVERY', s.content, s.hint, NULL
FROM (
    SELECT  1 AS ord, '화면을 직접 만들고 바로 결과를 보는 일이 즐겁다' AS content, 'FRONTEND' AS hint
    UNION ALL SELECT  2, '데이터를 모으고 정리해서 의미를 찾는 일이 즐겁다', 'DATA'
    UNION ALL SELECT  3, '눈에 보이지 않아도 요청을 처리하는 규칙과 로직을 짜는 일이 재미있다', 'BACKEND'
    UNION ALL SELECT  4, '보안 취약점을 찾고 막는 일에 흥미가 있다', 'SECURITY'
    UNION ALL SELECT  5, '여러 사람의 요구를 정리해 서비스 방향을 정하는 일이 좋다', 'PM'
    UNION ALL SELECT  6, '서비스가 멈추지 않고 안정적으로 돌아가도록 지켜보고 관리하는 일에 관심이 있다', 'DEVOPS'
    UNION ALL SELECT  7, '버튼 위치나 화면 흐름처럼 사용자가 느끼는 불편함이 먼저 눈에 들어온다', 'FRONTEND'
    UNION ALL SELECT  8, '여러 기능이 주고받을 데이터의 구조(테이블, API)를 정하는 일이 좋다', 'BACKEND'
    UNION ALL SELECT  9, '흩어진 파일이나 표를 한 가지 형식으로 정리해 두면 뿌듯하다', 'DATA'
    UNION ALL SELECT 10, '프로그램이 어떻게 뚫릴 수 있는지 거꾸로 생각해 보는 게 재미있다', 'SECURITY'
    UNION ALL SELECT 11, '설치·배포처럼 반복되는 작업을 자동으로 돌아가게 만드는 걸 좋아한다', 'DEVOPS'
    UNION ALL SELECT 12, '직접 코드를 짜는 것보다 일정과 우선순위를 정리하는 역할이 더 편하다', 'PM'
    UNION ALL SELECT 13, '웹사이트를 볼 때 색감·글자 크기·움직임 같은 디자인 요소를 자주 신경 쓴다', 'FRONTEND'
    UNION ALL SELECT 14, '비밀번호나 개인정보가 어떻게 안전하게 보관되는지 궁금하다', 'SECURITY'
    UNION ALL SELECT 15, '숫자나 그래프를 보고 왜 이런 결과가 나왔는지 따져보는 게 재미있다', 'DATA'
    UNION ALL SELECT 16, '서버나 클라우드 같은 환경을 직접 설정하고 구성해 보는 게 재미있다', 'DEVOPS'
    UNION ALL SELECT 17, '로그인·결제처럼 화면 뒤에서 정확하게 처리돼야 하는 기능을 만드는 데 관심이 있다', 'BACKEND'
    UNION ALL SELECT 18, '팀원들의 의견이 엇갈릴 때 정리해서 결론을 내는 역할을 자주 맡는다', 'PM'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM SURVEY_QUESTION q
    WHERE q.survey_type = 'JOB_DISCOVERY' AND q.content = s.content AND q.is_deleted = FALSE
)
ORDER BY s.ord;

-- 확인용: 계열별로 정확히 3문항씩 들어갔는지
-- SELECT job_category_hint, COUNT(*) FROM SURVEY_QUESTION
--  WHERE survey_type = 'JOB_DISCOVERY' AND is_deleted = FALSE
--  GROUP BY job_category_hint;
