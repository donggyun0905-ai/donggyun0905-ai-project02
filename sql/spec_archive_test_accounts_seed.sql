-- 스펙 아카이브 테스트 계정 — 글쓰기가 열리는 상위 2개 티어 계정을 하나씩 만든다.
-- 대상: 15_alter_tech_article_spec_archive.sql까지 실행한 DB. 여러 번 실행해도 계정이 겹쳐 생기지 않는다.
--
--   아이디 개척자     / 비밀번호 test1234 → 3,000점 (개척자 티어)
--   아이디 오디세이아 / 비밀번호 test1234 → 5,000점 (오디세이아 티어)
--
-- 비밀번호 해시는 web_session_test_seed.sql(webtest)과 같은 PasswordUtil.hash("test1234") 값 — 평문은 저장하지 않는다.
-- 점수는 ScoreService가 USER_SCORE_SUMMARY에 누적하는 방식이라(로그에서 다시 계산하지 않음) 요약 행만 넣어도 티어가 유지된다.
-- 티어 id는 하드코딩하지 않고 LEVEL_TIER에서 점수 구간으로 찾는다.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 계정
INSERT INTO USERS (user_type, login_id, password_hash, name, major, grade, desired_job_status,
                   privacy_consent_at, profile_updated_at)
SELECT 'APPLICANT', '개척자', '120000:ms2xNjDB1LUAROTu391FVg==:IpoaWkV+daypP6yGycCcel/6GjqGXZFMKiB6gMQcNKM=',
       '개척자', '컴퓨터공학과', '4학년', 'UNSET', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM USERS WHERE login_id = '개척자');

INSERT INTO USERS (user_type, login_id, password_hash, name, major, grade, desired_job_status,
                   privacy_consent_at, profile_updated_at)
SELECT 'APPLICANT', '오디세이아', '120000:ms2xNjDB1LUAROTu391FVg==:IpoaWkV+daypP6yGycCcel/6GjqGXZFMKiB6gMQcNKM=',
       '오디세이아', '컴퓨터공학과', '졸업', 'UNSET', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM USERS WHERE login_id = '오디세이아');

-- ---------------------------------------------------------------- 점수·티어
INSERT INTO USER_SCORE_SUMMARY (user_id, total_score, current_tier_id, streak_count)
SELECT u.id, 3000,
       (SELECT t.id FROM LEVEL_TIER t WHERE t.is_deleted = FALSE AND t.min_score <= 3000
        ORDER BY t.min_score DESC LIMIT 1),
       0
FROM USERS u WHERE u.login_id = '개척자'
ON DUPLICATE KEY UPDATE total_score = VALUES(total_score), current_tier_id = VALUES(current_tier_id), is_deleted = FALSE;

INSERT INTO USER_SCORE_SUMMARY (user_id, total_score, current_tier_id, streak_count)
SELECT u.id, 5000,
       (SELECT t.id FROM LEVEL_TIER t WHERE t.is_deleted = FALSE AND t.min_score <= 5000
        ORDER BY t.min_score DESC LIMIT 1),
       0
FROM USERS u WHERE u.login_id = '오디세이아'
ON DUPLICATE KEY UPDATE total_score = VALUES(total_score), current_tier_id = VALUES(current_tier_id), is_deleted = FALSE;

-- ---------------------------------------------------------------- 확인
SELECT u.login_id AS 아이디, s.total_score AS 점수, t.title_name AS 티어
FROM USERS u
JOIN USER_SCORE_SUMMARY s ON s.user_id = u.id
LEFT JOIN LEVEL_TIER t ON t.id = s.current_tier_id
WHERE u.login_id IN ('개척자', '오디세이아');
