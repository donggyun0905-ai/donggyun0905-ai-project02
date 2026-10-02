-- 스펙 오디세이 (Spec Odyssey) — 2주차 이후 초기 데이터
-- 대상: LEVEL_TIER(게이미피케이션 등급 5단계)
-- 기준 문서: requirements.md TD-5(b) 추천안 그대로 채택 (팀 확정, 2026-09-23)
-- problem_level_min/max는 TD-5(c) "우리 등급이 오를수록 더 높은 문제 레벨 추천"용 매핑값 — 1~5 스케일 추천안
-- title_name은 "항해사" 테마에서 "오디세이(여정)" 테마로 팀이 다시 정해서 교체함(2026-09-30) —
-- 공유 DB에는 이미 새 이름으로 들어가 있었는데 이 시드 파일만 갱신이 안 돼 있던 걸 뒤늦게 맞춤.

SET NAMES utf8mb4;

-- =========================================================
-- LEVEL_TIER — 게이미피케이션 등급 (5단계)
-- =========================================================
INSERT INTO LEVEL_TIER (min_score, max_score, tier_name, title_name, problem_level_min, problem_level_max) VALUES
    (0,    499,  '비기너',    '첫걸음',    0, 1),
    (500,  1499, '취준생',    '방랑자',    1, 2),
    (1500, 2999, '실전러',    '항해자',    2, 3),
    (3000, 4999, '취뽀 임박', '개척자',    3, 4),
    (5000, NULL, '취뽀',      '오디세이아', 4, 5);

-- ---------------------------------------------------------------------------
-- SCORING_RULE — 점수·복습 주기 기본값 (코드의 기본값과 같다)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO SCORING_RULE (rule_key, rule_value, description) VALUES
    ('LADDER_BUDGET',               2500, '직무 하나의 기술 사다리(입문→전문가)를 끝까지 했을 때 받는 총점. 기술이 몇 개든 같다'),
    ('WEIGHT_TIER_ENTRY',              1, '단계 가중치 — 입문'),
    ('WEIGHT_TIER_CORE',               2, '단계 가중치 — 핵심'),
    ('WEIGHT_TIER_ADVANCED',           2, '단계 가중치 — 심화'),
    ('WEIGHT_TIER_EXPERT',             3, '단계 가중치 — 전문가'),
    ('WEIGHT_IMPORTANCE_REQUIRED',     2, '중요도 가중치 — 필수 기술'),
    ('WEIGHT_IMPORTANCE_PREFERRED',    1, '중요도 가중치 — 우대 기술'),
    ('STEP_POINTS_MIN',                5, '기술 단계 1개가 주는 최소 점수'),
    ('FIXED_STEP_POINTS',            100, '자격증·프로젝트 단계 점수(기술 사다리 밖)'),
    ('ROUND_SKILL_COUNT',              5, '로드맵 한 라운드에 담는 기술 수'),
    ('REVIEW_POINTS_BASE',            40, '기술 복습 첫 점수'),
    ('REVIEW_POINTS_DECAY',           10, '같은 기술 복습을 할 때마다 줄어드는 점수'),
    ('REVIEW_POINTS_MIN',              5, '기술 복습 최저 점수'),
    ('REVIEW_DAYS_ENTRY',             30, '복습 주기(일) — 최고 단계 입문'),
    ('REVIEW_DAYS_CORE',              60, '복습 주기(일) — 최고 단계 핵심'),
    ('REVIEW_DAYS_ADVANCED',          90, '복습 주기(일) — 최고 단계 심화'),
    ('REVIEW_DAYS_EXPERT',           120, '복습 주기(일) — 최고 단계 전문가'),
    ('PROJECT_UPDATE_DAYS',           90, '프로젝트 업데이트 단계가 생기는 주기(일)'),
    ('ARTICLE_UPDATE_DAYS',          150, '기술 글 업데이트 단계가 생기는 주기(일)'),
    ('TREND_STUDY_DAYS',              30, '트렌딩 학습 단계가 생기는 주기(일)'),
    ('PROJECT_UPDATE_POINTS_BASE',    60, '프로젝트 업데이트 첫 점수'),
    ('ARTICLE_UPDATE_POINTS_BASE',    60, '기술 글 업데이트 첫 점수'),
    ('TREND_STUDY_POINTS',            40, '트렌딩 학습 점수(고정)'),
    ('UPKEEP_POINTS_DECAY',           10, '유지 단계 감쇠 폭'),
    ('UPKEEP_POINTS_MIN',              5, '유지 단계 최저 점수'),
    ('DAILY_POINTS_1',               6, '일일 문제 풀이 점수 — 등급 1번째(비기너)'),
    ('DAILY_POINTS_2',               6, '일일 문제 풀이 점수 — 등급 2번째(취준생)'),
    ('DAILY_POINTS_3',               8, '일일 문제 풀이 점수 — 등급 3번째(실전러)'),
    ('DAILY_POINTS_4',              10, '일일 문제 풀이 점수 — 등급 4번째(취뽀 임박)'),
    ('DAILY_POINTS_5',              12, '일일 문제 풀이 점수 — 등급 5번째(취뽀)'),
    ('STREAK_BONUS_PER_DAY',           2, '일일 문제 연속 보너스 — 연속 하루마다 늘어나는 점수(2일째부터)'),
    ('STREAK_BONUS_MAX',              20, '일일 문제 연속 보너스 상한(하루치)'),
    ('STREAK_BONUS_DAY7',             30, '7일 연속 달성일에 더 붙는 보너스'),
    ('STREAK_BONUS_DAY30',           100, '30일 연속 달성일에 더 붙는 보너스');
