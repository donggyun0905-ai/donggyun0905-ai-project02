-- =========================================================
-- 데이터 인사이트(/insights) 확인용 데모 데이터. 관련 요구사항: FR-45~48
-- 다시 실행해도 된다 — 맨 앞에서 이전 데모 데이터를 지우고 새로 넣는다.
-- 지울 때는 insight_demo_cleanup.sql
--
-- 로그인: insight_demo / demo1234!   (목표 직무: 백엔드 개발자, job_id = 1)
--   FR-45 또래 비교    — 컴퓨터공학·4학년 가상 사용자 5명(로그인 불가) 스냅샷, 평균 57 vs 나 64
--   FR-46 참고 루트    — 백엔드 개발자 4단계 8개 항목 (generated_at으로 데모 표시)
--   FR-47 요구 기술 변화 — 기존 JOB_SKILL_TREND 수집분을 그대로 쓴다 (넣지 않음)
--   FR-48 약점 히트맵  — 요구 기술 13개 중 6개 보유한 격차 분석 1건
-- =========================================================
SET NAMES utf8mb4;

-- ---------- 이전 데모 데이터 삭제 (insight_demo_cleanup.sql과 같은 내용) ----------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_users;
CREATE TEMPORARY TABLE tmp_demo_users AS
SELECT id FROM USERS WHERE login_id = 'insight_demo' OR login_id LIKE 'insight\_peer\_%';
DELETE FROM ROADMAP_STEP WHERE roadmap_id IN (SELECT id FROM ROADMAP WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM ROADMAP WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM GAP_ANALYSIS_ITEM WHERE gap_analysis_id IN (SELECT id FROM GAP_ANALYSIS WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM GAP_ANALYSIS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SHARE_LINK_VIEW_LOG WHERE share_link_id IN (SELECT id FROM SHARE_LINK WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM EVALUATION_SESSION_ITEM WHERE share_link_id IN (SELECT id FROM SHARE_LINK WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM SHARE_LINK WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM DOCUMENTS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_PROJECTS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SPECS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SKILLS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM JOB_RECOMMENDATION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SURVEY_ANSWER WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SPEC_SCORE_HISTORY WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_DAILY_MISSION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SCORE_LOG WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SCORE_SUMMARY WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM DDAY_ALERT WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM AI_USAGE_LOG WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USERS WHERE id IN (SELECT id FROM tmp_demo_users);
DELETE FROM JOB_BENCHMARK_SPEC WHERE job_id = 1 AND generated_at = '2026-10-01 00:00:00';
DROP TEMPORARY TABLE tmp_demo_users;

-- ---------- 데모 사용자 ----------
-- 비밀번호 demo1234! 의 PasswordUtil.hash 결과
INSERT INTO USERS (login_id, password_hash, email, major, grade, interest_field,
                   desired_job_id, desired_job_status, privacy_consent_at)
VALUES ('insight_demo', '120000:hYCRC+phpgxiUELAfKpCEQ==:T8nzlgvTiZHu/RZKQdag0AFzuFOdQae/y/AxfL3gGvE=',
        'insight_demo@example.com', '컴퓨터공학', '4', '백엔드', 1, 'SET', NOW());
SET @demo := LAST_INSERT_ID();

-- 또래 5명 — 비밀번호 해시가 형식에 맞지 않아 로그인할 수 없다
INSERT INTO USERS (login_id, password_hash, major, grade, desired_job_status, privacy_consent_at) VALUES
    ('insight_peer_1', '!demo-no-login', '컴퓨터공학', '4', 'UNSET', NOW()),
    ('insight_peer_2', '!demo-no-login', '컴퓨터공학', '4', 'UNSET', NOW()),
    ('insight_peer_3', '!demo-no-login', '컴퓨터공학', '4', 'UNSET', NOW()),
    ('insight_peer_4', '!demo-no-login', '컴퓨터공학', '4', 'UNSET', NOW()),
    ('insight_peer_5', '!demo-no-login', '컴퓨터공학', '4', 'UNSET', NOW());

-- ---------- FR-45 스펙 완성도 스냅샷 ----------
-- 또래는 어제 값과 오늘 값이 다르다 — 화면은 "가장 최근" 값만 평균에 넣는지 확인용
INSERT INTO SPEC_SCORE_HISTORY (user_id, snapshot_date, completeness_score, major, grade, is_seed)
SELECT u.id, CURDATE() - INTERVAL 1 DAY, v.prev_score, '컴퓨터공학', '4', TRUE
FROM USERS u JOIN (
    SELECT 'insight_peer_1' AS login_id, 30.00 AS prev_score, 45.00 AS score UNION ALL
    SELECT 'insight_peer_2', 40.00, 52.00 UNION ALL
    SELECT 'insight_peer_3', 50.00, 58.00 UNION ALL
    SELECT 'insight_peer_4', 55.00, 61.00 UNION ALL
    SELECT 'insight_peer_5', 60.00, 70.00
) v ON v.login_id = u.login_id;

INSERT INTO SPEC_SCORE_HISTORY (user_id, snapshot_date, completeness_score, major, grade, is_seed)
SELECT u.id, CURDATE(), v.score, '컴퓨터공학', '4', TRUE
FROM USERS u JOIN (
    SELECT 'insight_peer_1' AS login_id, 45.00 AS score UNION ALL
    SELECT 'insight_peer_2', 52.00 UNION ALL
    SELECT 'insight_peer_3', 58.00 UNION ALL
    SELECT 'insight_peer_4', 61.00 UNION ALL
    SELECT 'insight_peer_5', 70.00
) v ON v.login_id = u.login_id;

INSERT INTO SPEC_SCORE_HISTORY (user_id, snapshot_date, completeness_score, major, grade, is_seed) VALUES
    (@demo, CURDATE() - INTERVAL 1 DAY, 58.00, '컴퓨터공학', '4', TRUE),
    (@demo, CURDATE(), 64.00, '컴퓨터공학', '4', TRUE);

-- ---------- FR-46 백엔드 개발자 합격자 참고 루트 ----------
INSERT INTO JOB_BENCHMARK_SPEC (job_id, tier, spec_type, content, is_estimated, generated_at) VALUES
    (1, 'ENTRY',    'CERT',    '정보처리기사',                        TRUE, '2026-10-01 00:00:00'),
    (1, 'ENTRY',    'PROJECT', 'Java·Spring으로 만든 CRUD 프로젝트 1개', TRUE, '2026-10-01 00:00:00'),
    (1, 'CORE',     'SKILL',   'JPA·MySQL로 만든 서비스 배포',          TRUE, '2026-10-01 00:00:00'),
    (1, 'CORE',     'CERT',    'SQLD',                                TRUE, '2026-10-01 00:00:00'),
    (1, 'ADVANCED', 'SKILL',   'Redis 캐시로 응답 속도 개선',           TRUE, '2026-10-01 00:00:00'),
    (1, 'ADVANCED', 'SKILL',   'Docker·AWS 배포 자동화',               TRUE, '2026-10-01 00:00:00'),
    (1, 'EXPERT',   'PROJECT', '실사용자가 있는 서비스 운영 경험',        TRUE, '2026-10-01 00:00:00'),
    (1, 'EXPERT',   'SKILL',   'Kubernetes 운영 · 오픈소스 기여',       TRUE, '2026-10-01 00:00:00');

-- ---------- FR-48 보유 기술 + 격차 분석 ----------
-- 백엔드 개발자 요구 기술 중 Java, Python, SQL, MySQL, Git, REST API 6개 보유
INSERT INTO USER_SKILLS (user_id, skill_id, raw_input, similarity_score, proficiency)
SELECT @demo, s.id, s.skill_name, 1.0000, 'INTERMEDIATE'
FROM SKILL s
WHERE s.skill_name IN ('Java', 'Python', 'SQL', 'MySQL', 'Git', 'REST API') AND s.is_deleted = FALSE;

INSERT INTO GAP_ANALYSIS (user_id, job_id, match_rate, analyzed_at)
SELECT @demo, 1,
       ROUND(100 * SUM(us.skill_id IS NOT NULL) / COUNT(*), 2), NOW()
FROM JOB_REQUIRED_SKILL r
LEFT JOIN USER_SKILLS us ON us.user_id = @demo AND us.skill_id = r.skill_id
WHERE r.job_id = 1 AND r.is_deleted = FALSE;
SET @gap := LAST_INSERT_ID();

INSERT INTO GAP_ANALYSIS_ITEM (gap_analysis_id, skill_id, status, similarity_score)
SELECT @gap, r.skill_id,
       IF(us.skill_id IS NULL, 'MISSING', 'MET'),
       IF(us.skill_id IS NULL, NULL, 1.0000)
FROM JOB_REQUIRED_SKILL r
LEFT JOIN USER_SKILLS us ON us.user_id = @demo AND us.skill_id = r.skill_id
WHERE r.job_id = 1 AND r.is_deleted = FALSE;
