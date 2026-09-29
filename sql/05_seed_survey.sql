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
--   1) 직무 계열 6개 × 2문항 = 12문항. 한 문항이 두 계열을 동시에 가리키지 않게 함
--      (기존 3번 "서버·시스템 안정성"은 백엔드/DevOps 가 섞여 있어서 둘로 쪼갬)
--   2) 기술 용어를 몰라도 답할 수 있게 "하는 일"로 물어봄 (희망 직무가 없는 사람이 대상)
--   3) 같은 계열 문항이 붙어 나오지 않게 순서를 섞음 (응답 쏠림 방지)
--   4) 화면설계서 7번의 기존 5문항 중 4개는 문장 그대로 유지 (1, 2, 4, 5번)
-- 실행 순서: 01_schema.sql → 02_seed.sql → 03_schema_extended.sql → 04_seed_skills.sql → 이 파일
-- =============================================================

SET NAMES utf8mb4;

INSERT INTO SURVEY_QUESTION (survey_type, content, job_category_hint, score_weight) VALUES
('JOB_DISCOVERY', '화면을 직접 만들고 바로 결과를 보는 일이 즐겁다',                         'FRONTEND', NULL),
('JOB_DISCOVERY', '데이터를 모으고 정리해서 의미를 찾는 일이 즐겁다',                         'DATA',     NULL),
('JOB_DISCOVERY', '눈에 보이지 않아도 요청을 처리하는 규칙과 로직을 짜는 일이 재미있다',          'BACKEND',  NULL),
('JOB_DISCOVERY', '보안 취약점을 찾고 막는 일에 흥미가 있다',                                 'SECURITY', NULL),
('JOB_DISCOVERY', '여러 사람의 요구를 정리해 서비스 방향을 정하는 일이 좋다',                  'PM',       NULL),
('JOB_DISCOVERY', '서비스가 멈추지 않고 안정적으로 돌아가도록 지켜보고 관리하는 일에 관심이 있다', 'DEVOPS',   NULL),
('JOB_DISCOVERY', '버튼 위치나 화면 흐름처럼 사용자가 느끼는 불편함이 먼저 눈에 들어온다',        'FRONTEND', NULL),
('JOB_DISCOVERY', '여러 기능이 주고받을 데이터의 구조(테이블, API)를 정하는 일이 좋다',          'BACKEND',  NULL),
('JOB_DISCOVERY', '흩어진 파일이나 표를 한 가지 형식으로 정리해 두면 뿌듯하다',                  'DATA',     NULL),
('JOB_DISCOVERY', '프로그램이 어떻게 뚫릴 수 있는지 거꾸로 생각해 보는 게 재미있다',             'SECURITY', NULL),
('JOB_DISCOVERY', '설치·배포처럼 반복되는 작업을 자동으로 돌아가게 만드는 걸 좋아한다',          'DEVOPS',   NULL),
('JOB_DISCOVERY', '직접 코드를 짜는 것보다 일정과 우선순위를 정리하는 역할이 더 편하다',          'PM',       NULL);

-- 확인용: 계열별로 정확히 2문항씩 들어갔는지
-- SELECT job_category_hint, COUNT(*) FROM SURVEY_QUESTION
--  WHERE survey_type = 'JOB_DISCOVERY' AND is_deleted = FALSE
--  GROUP BY job_category_hint;
