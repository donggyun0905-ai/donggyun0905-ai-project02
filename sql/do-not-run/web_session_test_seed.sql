-- 웹 클라우드 세션에서 바로 로그인해서 테스트할 수 있게 만든 계정 + 격차분석 시드
-- (2026-10-01, DB 연결이 안 되는 클라우드 세션 환경 지원용). sql/do-not-run/schema_snapshot_2026-10-01.sql +
-- 기존 02_seed.sql~11_seed_skill_alias_english.sql로 기본 데이터(직무·스킬·자격증 등)가 이미 들어간
-- DB에 이 파일을 실행하면 된다.
--
-- 로그인 계정: 아이디 webtest / 비밀번호 test1234
--   (비밀번호 해시는 PasswordUtil.hash("test1234")로 직접 생성한 값 — 평문은 저장 안 함, claude.md 준수)
--
-- 희망 직무를 "API 개발자"로 설정하고, 그 직무의 REQUIRED 기술 상위 5개를 부족(MISSING)으로 미리
-- 넣어뒀다 — 로그인 후 /roadmap에서 "로드맵 생성하기" 버튼 한 번만 누르면 바로 5개 기술 ×
-- 4개 티어(입문~전문가) 로드맵이 만들어진다. 단계를 하나씩 완료하면서 티어 돌파 모달·초록색 완료
-- 표시·좌우 위젯 등을 바로 확인할 수 있다.

INSERT INTO USERS (login_id, password_hash, major, grade, desired_job_id, desired_job_status,
                    privacy_consent_at, profile_updated_at, created_at, updated_at, is_deleted)
SELECT 'webtest', '120000:ms2xNjDB1LUAROTu391FVg==:IpoaWkV+daypP6yGycCcel/6GjqGXZFMKiB6gMQcNKM=',
       '컴퓨터공학과', '4학년', j.id, 'SET', NOW(), NOW(), NOW(), NOW(), FALSE
FROM JOB j
WHERE j.job_name = 'API 개발자'
LIMIT 1;

SET @test_user_id = LAST_INSERT_ID();
SET @test_job_id = (SELECT desired_job_id FROM USERS WHERE id = @test_user_id);

INSERT INTO GAP_ANALYSIS (user_id, job_id, match_rate, job_requirement_version, analyzed_at,
                           created_at, updated_at, is_deleted)
SELECT @test_user_id, @test_job_id, 30.00, j.requirement_version, NOW(), NOW(), NOW(), FALSE
FROM JOB j
WHERE j.id = @test_job_id;

SET @test_gap_id = LAST_INSERT_ID();

-- REQUIRED 기술 상위 5개를 MISSING으로 — RoadmapService가 라운드(5개)를 그대로 채울 수 있게.
INSERT INTO GAP_ANALYSIS_ITEM (gap_analysis_id, skill_id, status, created_at, updated_at, is_deleted)
SELECT @test_gap_id, r.skill_id, 'MISSING', NOW(), NOW(), FALSE
FROM JOB_REQUIRED_SKILL r
WHERE r.job_id = @test_job_id AND r.importance = 'REQUIRED'
ORDER BY r.id
LIMIT 5;

SELECT CONCAT('테스트 계정 준비 완료 — user_id=', @test_user_id, ', gap_analysis_id=', @test_gap_id,
              ' (로그인: webtest / test1234)') AS result;
