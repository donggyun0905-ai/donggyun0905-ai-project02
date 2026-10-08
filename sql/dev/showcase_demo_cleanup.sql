-- =========================================================
-- 발표 시연용 계정(showcase_demo_seed.sql) 삭제 — demo · demo_hr · demo_peer_1·2와 이 계정들이 남긴 모든 행
-- 시뮬레이션으로 쌓인 격차 분석·로드맵·미션·점수 기록도 함께 지운다. 시연 전용 데이터라 물리 삭제한다.
-- =========================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_demo_users;
CREATE TEMPORARY TABLE tmp_demo_users AS
SELECT id FROM USERS WHERE login_id IN ('demo', 'demo_hr', 'demo_peer_1', 'demo_peer_2');

DROP TEMPORARY TABLE IF EXISTS tmp_demo_links;
CREATE TEMPORARY TABLE tmp_demo_links AS
SELECT id FROM SHARE_LINK WHERE user_id IN (SELECT id FROM tmp_demo_users);

DROP TEMPORARY TABLE IF EXISTS tmp_demo_articles;
CREATE TEMPORARY TABLE tmp_demo_articles AS
SELECT id FROM TECH_ARTICLE WHERE user_id IN (SELECT id FROM tmp_demo_users);

DROP TEMPORARY TABLE IF EXISTS tmp_demo_projects;
CREATE TEMPORARY TABLE tmp_demo_projects AS
SELECT id FROM USER_PROJECTS WHERE user_id IN (SELECT id FROM tmp_demo_users);

-- 면접관 비교함 · 공유 링크
DELETE FROM EVALUATION_SESSION_ITEM WHERE share_link_id IN (SELECT id FROM tmp_demo_links)
    OR session_id IN (SELECT id FROM EVALUATION_SESSION WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM EVALUATION_CRITERIA WHERE session_id IN (SELECT id FROM EVALUATION_SESSION WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM EVALUATION_SESSION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SHARE_LINK_VIEW_LOG WHERE share_link_id IN (SELECT id FROM tmp_demo_links);
DELETE FROM SHARE_LINK WHERE id IN (SELECT id FROM tmp_demo_links);

-- 스펙 아카이브 — 시연 계정이 쓴 글에 달린 것 + 시연 계정이 남긴 것
DELETE FROM TECH_ARTICLE_COMMENT WHERE (article_id IN (SELECT id FROM tmp_demo_articles)
    OR user_id IN (SELECT id FROM tmp_demo_users)) AND parent_comment_id IS NOT NULL;
DELETE FROM TECH_ARTICLE_COMMENT WHERE article_id IN (SELECT id FROM tmp_demo_articles)
    OR user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM TECH_ARTICLE_LIKE WHERE article_id IN (SELECT id FROM tmp_demo_articles) OR user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM TECH_ARTICLE_BOOKMARK WHERE article_id IN (SELECT id FROM tmp_demo_articles) OR user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM TECH_ARTICLE_REPORT WHERE article_id IN (SELECT id FROM tmp_demo_articles) OR reporter_user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM TECH_ARTICLE_VIEW_LOG WHERE article_id IN (SELECT id FROM tmp_demo_articles) OR viewer_user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM TECH_ARTICLE_ATTACHMENT WHERE article_id IN (SELECT id FROM tmp_demo_articles);
DELETE FROM TECH_ARTICLE WHERE id IN (SELECT id FROM tmp_demo_articles);

-- 로드맵 · 격차 분석 (시뮬레이션이 만든 것 포함)
UPDATE USERS SET resume_document_id = NULL, cover_letter_document_id = NULL WHERE id IN (SELECT id FROM tmp_demo_users);
DELETE FROM PROJECT_DOCUMENT_ITEM WHERE project_id IN (SELECT id FROM tmp_demo_projects);
DELETE FROM DOCUMENTS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM ROADMAP_STEP WHERE roadmap_id IN (SELECT id FROM ROADMAP WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM ROADMAP WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM GAP_ANALYSIS_ITEM WHERE gap_analysis_id IN (SELECT id FROM GAP_ANALYSIS WHERE user_id IN (SELECT id FROM tmp_demo_users));
DELETE FROM GAP_ANALYSIS WHERE user_id IN (SELECT id FROM tmp_demo_users);

-- 프로필
DELETE FROM PROJECT_LINK WHERE project_id IN (SELECT id FROM tmp_demo_projects);
DELETE FROM PROJECT_TECH_NOTE WHERE project_id IN (SELECT id FROM tmp_demo_projects);
UPDATE USER_PROJECTS SET upgraded_from_project_id = NULL WHERE id IN (SELECT id FROM tmp_demo_projects);
DELETE FROM USER_PROJECTS WHERE id IN (SELECT id FROM tmp_demo_projects);
DELETE FROM USER_SPECS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SKILLS WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_EDUCATION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM JOB_RECOMMENDATION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SURVEY_ANSWER WHERE user_id IN (SELECT id FROM tmp_demo_users);

-- 활동 기록
DELETE FROM SPEC_SCORE_HISTORY WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_DAILY_MISSION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SCORE_LOG WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM USER_SCORE_SUMMARY WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM SIMULATION_STATE WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM DDAY_ALERT WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM NOTIFICATION WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM AI_USAGE_LOG WHERE user_id IN (SELECT id FROM tmp_demo_users);
DELETE FROM COMPANION_DEVICE WHERE user_id IN (SELECT id FROM tmp_demo_users);

DELETE FROM USERS WHERE id IN (SELECT id FROM tmp_demo_users);

DROP TEMPORARY TABLE tmp_demo_users;
DROP TEMPORARY TABLE tmp_demo_links;
DROP TEMPORARY TABLE tmp_demo_articles;
DROP TEMPORARY TABLE tmp_demo_projects;
