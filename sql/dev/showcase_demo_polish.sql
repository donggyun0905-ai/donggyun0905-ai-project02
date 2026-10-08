-- =========================================================
-- 발표 시연용 계정 마무리 — 시뮬레이션이 끝난 뒤 실행한다 (showcase_demo_seed.sql → demo 시뮬레이션 → 이 파일)
-- 시뮬레이션은 증빙 칸에 "시뮬레이션 학습 기록 —" 같은 고정 문구를 남기고, 기술·자격증·프로젝트 단계는 증빙 없이 끝낸다.
-- 화면에서 실제 사용자가 제출한 것처럼 보이도록 증빙 종류·내용·판정 문구만 채운다. 점수·완료일·순서는 건드리지 않는다.
-- 다시 실행해도 된다.
-- =========================================================
SET NAMES utf8mb4;

SET @demo := (SELECT id FROM USERS WHERE login_id = 'demo' AND is_deleted = FALSE);
SET @proj1 := (SELECT id FROM USER_PROJECTS WHERE user_id = @demo AND title = '캠퍼스 중고거래 API');
SET @proj2 := (SELECT id FROM USER_PROJECTS WHERE user_id = @demo AND title = '혼자 쓰는 가계부 웹앱');

-- 입문(ENTRY) 기술 단계 — 공부노트 (SkillProofGrader 입문 기준: 300자 이상 · 기술명 2회 이상)
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
JOIN SKILL k ON k.id = s.related_skill_id
SET s.proof_type = 'NOTE',
    s.proof_content = CONCAT(
        '[', k.skill_name, ' 공부노트]\n\n',
        '1. 왜 필요한가\n', k.skill_name, '은(는) 백엔드 개발자 채용 공고에서 자주 요구되는 기술이다. ',
        '공식 문서의 시작하기 가이드를 따라 하면서 핵심 개념과 용어를 정리했다.\n\n',
        '2. 핵심 개념\n- 기본 구조와 동작 원리를 그림으로 그려 보며 이해했다.\n',
        '- 자주 쓰는 설정과 명령을 표로 정리하고, 각각 언제 쓰는지 예시를 붙였다.\n',
        '- 처음에 헷갈렸던 부분은 직접 예제를 바꿔 가며 결과를 확인했다.\n\n',
        '3. 실습\n```\n# ', k.skill_name, ' 예제를 로컬에서 실행하고 결과를 캡처했다\n```\n\n',
        '4. 다음에 할 것\n', k.skill_name, '을(를) 캠퍼스 중고거래 API에 적용해 보고, 바뀐 점을 프로젝트 회고에 남긴다.'),
    s.review_status = 'PASSED',
    s.review_note = '자동 판정 통과 (글자 수 412자, 기술명 언급 3회)'
WHERE r.user_id = @demo AND s.step_type = 'SKILL' AND s.tier = 'ENTRY' AND s.is_completed = TRUE;

-- 핵심(CORE) · 심화(ADVANCED) 기술 단계 — 프로젝트로 확인
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
SET s.proof_type = 'PROJECT_LINK',
    s.evidence_project_id = IF(s.tier = 'CORE', @proj1, @proj2),
    s.proof_content = NULL,
    s.review_status = 'PASSED',
    s.review_note = '프로젝트 등록/업그레이드로 자동 확인'
WHERE r.user_id = @demo AND s.step_type = 'SKILL' AND s.tier IN ('CORE', 'ADVANCED') AND s.is_completed = TRUE;

-- 전문가(EXPERT) 기술 단계 — 기술 설명 글
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
JOIN SKILL k ON k.id = s.related_skill_id
SET s.proof_type = 'TEACHING_POST',
    s.proof_content = CONCAT('[', k.skill_name, ' 설명 글] 처음 배우는 사람을 위한 ', k.skill_name,
                             ' 정리 — 개념, 예제 코드, 실제 프로젝트에서 겪은 문제와 해결 과정을 순서대로 설명했다.'),
    s.review_status = 'PASSED',
    s.review_note = '자동 판정 통과 (글자 수 1,124자, 기술명 언급 6회, 코드 예시 포함)'
WHERE r.user_id = @demo AND s.step_type = 'SKILL' AND s.tier = 'EXPERT' AND s.is_completed = TRUE;

-- 자격증 단계 — 증빙 서류 제출
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
SET s.proof_type = 'CERT_DOCUMENT',
    s.review_status = 'PASSED',
    s.review_note = '자격증 증빙 서류 제출로 확인'
WHERE r.user_id = @demo AND s.step_type = 'CERT' AND s.is_completed = TRUE;

-- 복습 — 시뮬레이션 고정 문구를 기술별 복습 기록으로
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
LEFT JOIN SKILL k ON k.id = s.related_skill_id
SET s.proof_content = CONCAT(COALESCE(k.skill_name, '지난 학습'),
        ' 복습 — 한 달 전 공부노트를 다시 읽고, 기억나지 않던 부분을 예제로 다시 따라 쳐 봤다. ',
        '헷갈렸던 개념 두 가지를 노트 맨 위에 요약해 두었다.')
WHERE r.user_id = @demo AND s.step_type = 'REVIEW' AND s.proof_content LIKE '시뮬레이션%';

-- 트렌딩 학습 — reason의 "트렌딩 학습 — 기술명: 설명"에서 기술명을 꺼내 학습 기록으로
UPDATE ROADMAP_STEP s
JOIN ROADMAP r ON r.id = s.roadmap_id
SET s.proof_content = CONCAT(
        TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(s.reason, '—', -1), ':', 1)),
        ' 공식 문서의 소개와 튜토리얼을 읽고, 지금 쓰는 기술과 무엇이 다른지 세 줄로 정리했다. ',
        '실무에서 어떤 문제를 풀 때 쓰이는지 채용 공고 두 건에서 확인했다.')
WHERE r.user_id = @demo AND s.step_type = 'TREND_STUDY' AND s.proof_content LIKE '시뮬레이션%';

-- =========================================================
-- 날짜 당기기 — 마지막 미션 날이 "어제"가 되도록 시연 계정의 모든 날짜를 같은 날 수만큼 옮긴다.
-- 시뮬레이션은 대개 며칠 전에 끝나서 그대로 두면 연속 기록이 끊겨 보인다.
-- 발표 당일 아침에 이 파일을 한 번 더 실행하면 연속 기록 · D-day · 알림 시각이 그날 기준으로 맞춰진다.
-- (오늘 미션은 비워 두므로 시연 중에 직접 풀어 연속 기록을 이어 갈 수 있다)
-- =========================================================
SET @shift := GREATEST(0, DATEDIFF(CURDATE() - INTERVAL 1 DAY,
        (SELECT last_mission_date FROM USER_SCORE_SUMMARY WHERE user_id = @demo)));
SET @hr := (SELECT id FROM USERS WHERE login_id = 'demo_hr');

-- 날짜가 UNIQUE에 들어간 테이블은 뒤에서부터 옮겨야 중간에 겹치지 않는다
UPDATE USER_DAILY_MISSION
SET assigned_date = assigned_date + INTERVAL @shift DAY, completed_at = completed_at + INTERVAL @shift DAY,
    submitted_at = submitted_at + INTERVAL @shift DAY, created_at = created_at + INTERVAL @shift DAY,
    updated_at = updated_at + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0
ORDER BY assigned_date DESC;

UPDATE SPEC_SCORE_HISTORY
SET snapshot_date = snapshot_date + INTERVAL @shift DAY, created_at = created_at + INTERVAL @shift DAY,
    updated_at = updated_at + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0
ORDER BY snapshot_date DESC;

UPDATE SCORE_LOG SET earned_at = earned_at + INTERVAL @shift DAY, created_at = created_at + INTERVAL @shift DAY,
    updated_at = updated_at + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0;
UPDATE USER_SCORE_SUMMARY SET last_mission_date = last_mission_date + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0;
UPDATE SIMULATION_STATE SET start_date = start_date + INTERVAL @shift DAY WHERE user_id = @demo AND @shift > 0;

UPDATE GAP_ANALYSIS SET analyzed_at = analyzed_at + INTERVAL @shift DAY, created_at = created_at + INTERVAL @shift DAY,
    updated_at = updated_at + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0;
UPDATE ROADMAP SET created_at = created_at + INTERVAL @shift DAY, updated_at = updated_at + INTERVAL @shift DAY
WHERE user_id = @demo AND @shift > 0;
UPDATE ROADMAP_STEP s JOIN ROADMAP r ON r.id = s.roadmap_id
SET s.completed_at = s.completed_at + INTERVAL @shift DAY, s.created_at = s.created_at + INTERVAL @shift DAY,
    s.updated_at = s.updated_at + INTERVAL @shift DAY
WHERE r.user_id = @demo AND @shift > 0;

-- 시드가 넣은 것 — 옮기지 않고 "오늘" 기준으로 다시 맞춘다 (시드와 같은 간격)
UPDATE DDAY_ALERT SET target_date = CURDATE() + INTERVAL CASE title
        WHEN '스펙컴퍼니 백엔드 공채 서류 마감' THEN 1
        WHEN 'AWS Certified Developer - Associate 시험' THEN 18
        WHEN '리눅스마스터 2급 실기' THEN 37
        WHEN '포트폴리오 최종 점검' THEN 7 END DAY,
    is_notified = (title = '스펙컴퍼니 백엔드 공채 서류 마감')
WHERE user_id = @demo AND title IN ('스펙컴퍼니 백엔드 공채 서류 마감', 'AWS Certified Developer - Associate 시험',
                                    '리눅스마스터 2급 실기', '포트폴리오 최종 점검');

UPDATE NOTIFICATION SET created_at = NOW() - INTERVAL CASE
        WHEN ref_key LIKE '%:demo1' AND noti_type = 'SHARE_VIEW' THEN 5 * 24 + 3
        WHEN ref_key LIKE '%:demo1' AND noti_type = 'COMMENT' THEN 8 * 24
        WHEN ref_key LIKE '%:demo2' THEN 26
        WHEN ref_key LIKE '%:demo3' THEN 5
        ELSE 1 END HOUR,
    ref_key = IF(noti_type = 'DDAY', CONCAT('dday:demo:', CURDATE()), ref_key),
    is_read = ref_key LIKE '%:demo1'
WHERE user_id = @demo AND (ref_key LIKE '%:demo_' OR ref_key LIKE 'dday:demo:%');

SET @link_full := (SELECT id FROM SHARE_LINK WHERE token = 'demo-showcase-full-7Qm2xVb9LkP4sWn8');
SET @link_lite := (SELECT id FROM SHARE_LINK WHERE token = 'demo-showcase-lite-Hc3Ja8Rt5Ye1Uo6Z');
DELETE FROM SHARE_LINK_VIEW_LOG WHERE share_link_id IN (@link_full, @link_lite) AND viewer_ip LIKE '%.%.%.%'
    AND (viewer_ip LIKE '203.0.113.%' OR viewer_ip LIKE '198.51.100.%');
INSERT INTO SHARE_LINK_VIEW_LOG (share_link_id, viewed_at, viewer_ip) VALUES
    (@link_full, NOW() - INTERVAL 5 DAY - INTERVAL 3 HOUR, '203.0.113.21'),
    (@link_full, NOW() - INTERVAL 4 DAY - INTERVAL 7 HOUR, '203.0.113.21'),
    (@link_full, NOW() - INTERVAL 2 DAY - INTERVAL 1 HOUR, '203.0.113.48'),
    (@link_full, NOW() - INTERVAL 5 HOUR, '203.0.113.21'),
    (@link_lite, NOW() - INTERVAL 1 DAY - INTERVAL 2 HOUR, '198.51.100.7');

UPDATE SHARE_LINK SET
    expires_at = NOW() + INTERVAL CASE token
        WHEN 'demo-showcase-lite-Hc3Ja8Rt5Ye1Uo6Z' THEN 14 ELSE 30 END DAY,
    created_at = NOW() - INTERVAL CASE token
        WHEN 'demo-showcase-full-7Qm2xVb9LkP4sWn8' THEN 6
        WHEN 'demo-showcase-lite-Hc3Ja8Rt5Ye1Uo6Z' THEN 3
        WHEN 'demo-peer1-Bx7Nq2Lm9Vc4Kd8Rs1Wt' THEN 5
        ELSE 4 END DAY
WHERE token IN ('demo-showcase-full-7Qm2xVb9LkP4sWn8', 'demo-showcase-lite-Hc3Ja8Rt5Ye1Uo6Z',
                'demo-peer1-Bx7Nq2Lm9Vc4Kd8Rs1Wt', 'demo-peer2-Fp5Gh1Jk8Zx3Cv6Bn0Mq');

UPDATE EVALUATION_SESSION_ITEM i JOIN SHARE_LINK k ON k.id = i.share_link_id
SET i.added_at = NOW() - INTERVAL IF(k.token LIKE 'demo-peer2-%', 4, 5) DAY
WHERE k.token IN ('demo-showcase-full-7Qm2xVb9LkP4sWn8', 'demo-peer1-Bx7Nq2Lm9Vc4Kd8Rs1Wt', 'demo-peer2-Fp5Gh1Jk8Zx3Cv6Bn0Mq');

UPDATE TECH_ARTICLE SET
    published_at = NOW() - INTERVAL IF(title LIKE '목록 조회 820ms%', 9, 20) DAY,
    created_at = NOW() - INTERVAL IF(title LIKE '목록 조회 820ms%', 9, 20) DAY
WHERE user_id = @demo AND (title LIKE '목록 조회 820ms%' OR title LIKE '정보처리기사 실기, 3주%');
UPDATE TECH_ARTICLE_COMMENT c JOIN TECH_ARTICLE a ON a.id = c.article_id
SET c.created_at = NOW() - INTERVAL 8 DAY
WHERE a.user_id = @demo AND a.title LIKE '목록 조회 820ms%' AND c.parent_comment_id IS NULL
  AND c.content LIKE '커서 페이지네이션에서 created_at이 같은 행%';

UPDATE USERS SET last_login_at = NOW() WHERE id = @demo;

-- =========================================================
-- 스펙 완성도 기록 고르게 펴기 — 시뮬레이션은 첫 3주 안에 프로필을 거의 다 채워서 그 뒤로는 같은 값만 이어진다.
-- 면접관 뷰 성장 잠재력(주·월·년 그래프)에서 꾸준히 오르는 모습이 보이도록, 첫 값과 마지막 값은 그대로 두고
-- 그 사이를 사흘 간격 계단으로 고르게 올린다. 첫 값·마지막 값이 같으면 결과도 같아서 다시 실행해도 된다.
-- =========================================================
UPDATE SPEC_SCORE_HISTORY h
JOIN (
    SELECT id,
           ROW_NUMBER() OVER w - 1 AS rn,
           COUNT(*) OVER () AS n,
           FIRST_VALUE(completeness_score) OVER w_all AS first_v,
           LAST_VALUE(completeness_score) OVER w_all AS last_v
    FROM SPEC_SCORE_HISTORY
    WHERE user_id = @demo AND is_deleted = FALSE
    WINDOW w AS (ORDER BY snapshot_date),
           w_all AS (ORDER BY snapshot_date ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
) t ON t.id = h.id
SET h.completeness_score = IF(t.rn = t.n - 1, t.last_v,
        ROUND(t.first_v + (t.last_v - t.first_v) * POW(LEAST(1, FLOOR(t.rn / 3) * 3 / (t.n - 1)), 0.85), 2))
WHERE t.n > 2;
