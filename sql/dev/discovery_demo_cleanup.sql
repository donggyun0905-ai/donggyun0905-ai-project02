-- =========================================================
-- 직무 발굴 전공 역산 확인용 데모 데이터 삭제 (discovery_demo_seed.sql로 넣은 것 전부)
-- 대상: login_id가 discovery_demo_ 로 시작하는 사용자와 그 사용자의 모든 행
-- 데모 전용 데이터라 논리 삭제가 아니라 실제로 지운다.
-- =========================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_demo_users;
CREATE TEMPORARY TABLE tmp_demo_users AS
SELECT id FROM USERS WHERE login_id LIKE 'discovery\_demo\_%';

-- 손자 테이블 → 자식 테이블 → USERS 순 (확인하다 격차 분석·로드맵까지 넘어갔어도 같이 지운다)
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

DROP TEMPORARY TABLE tmp_demo_users;
