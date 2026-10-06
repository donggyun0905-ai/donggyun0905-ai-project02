-- =========================================================
-- 직무 발굴(/job-discovery) 전공 역산 확인용 데모 사용자. 관련 요구사항: FR-38 ②
-- 다시 실행해도 된다 — 맨 앞에서 이전 데모 데이터를 지우고 새로 넣는다.
-- 지울 때는 discovery_demo_cleanup.sql
--
-- 로그인 비밀번호는 전부 demo1234!
-- 다섯 명 모두 보유 기술 없음 + 설문 전 문항 3점(중립)이 미리 체크돼 있다 → 제출만 누르면
-- 전공 하나만으로 순위가 갈리는지 볼 수 있다. (모델 EMBEDDING_MODEL_DIR이 있는 서버에서만 전공이 반영됨)
--
--   discovery_demo_stat     통계학과       → 1순위 데이터 계열 + 추천 이유에 "전공(통계학과)도 …"
--   discovery_demo_sec      정보보호학과   → 1순위 보안 계열 + 전공 문장
--   discovery_demo_design   시각디자인학과 → 1순위 프론트엔드 계열 + 전공 문장
--   discovery_demo_cs       컴퓨터공학과   → 전공 신호 약함(신뢰도 0.3 미만) — 전공 미반영, 전공 없는 사용자와 같은 결과
--   discovery_demo_korean   국어국문학과   → IT와 거리가 멂(신뢰도 0.3 미만) — 전공 미반영, 전공 없는 사용자와 같은 결과
-- =========================================================
SET NAMES utf8mb4;

-- ---------- 이전 데모 데이터 삭제 (discovery_demo_cleanup.sql과 같은 내용) ----------
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

-- ---------- 데모 사용자 ----------
-- 비밀번호 demo1234! 의 PasswordUtil.hash 결과 (insight_demo_seed.sql과 같은 값)
SET @pw := '120000:hYCRC+phpgxiUELAfKpCEQ==:T8nzlgvTiZHu/RZKQdag0AFzuFOdQae/y/AxfL3gGvE=';
INSERT INTO USERS (login_id, password_hash, name, email, major, grade, desired_job_status, privacy_consent_at) VALUES
    ('discovery_demo_stat',   @pw, '통계 데모',     'discovery_demo_stat@example.com',   '통계학과',       '4', 'UNSET', NOW()),
    ('discovery_demo_sec',    @pw, '정보보호 데모', 'discovery_demo_sec@example.com',    '정보보호학과',   '4', 'UNSET', NOW()),
    ('discovery_demo_design', @pw, '디자인 데모',   'discovery_demo_design@example.com', '시각디자인학과', '4', 'UNSET', NOW()),
    ('discovery_demo_cs',     @pw, '컴공 데모',     'discovery_demo_cs@example.com',     '컴퓨터공학과',   '4', 'UNSET', NOW()),
    ('discovery_demo_korean', @pw, '국문 데모',     'discovery_demo_korean@example.com', '국어국문학과',   '4', 'UNSET', NOW());

-- ---------- 설문 응답: 직무 발굴 전 문항 3점 ----------
INSERT INTO USER_SURVEY_ANSWER (user_id, question_id, answer_value, answered_at)
SELECT u.id, q.id, 3, NOW()
FROM USERS u
JOIN SURVEY_QUESTION q ON q.survey_type = 'JOB_DISCOVERY' AND q.is_deleted = FALSE
WHERE u.login_id LIKE 'discovery\_demo\_%';
