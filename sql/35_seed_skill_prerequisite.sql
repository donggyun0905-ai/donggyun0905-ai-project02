-- 스펙 오디세이 (Spec Odyssey) — 기술 선수관계 초기 데이터 (2026-10-08)
-- 대상: 34번까지 실행한 DB. 여러 번 실행해도 안전하다(이름으로 찾고 중복은 무시).
--
-- 로드맵이 위상 정렬로 순서를 정하려면 관계가 있어야 한다. "이걸 모르면 저걸 공부할 수 없다"가
-- 분명한 쌍만 넣는다 — 애매한 것(예: React 전에 TypeScript)은 넣지 않는다. 취향이 섞이면
-- "왜 이 순서냐"에 답할 수 없게 되고, 관리자 화면에서 언제든 더할 수 있다.
--
-- 이름으로 찾으므로 시드에 없는 기술이 섞여 있으면 그 줄만 조용히 건너뛴다(INNER JOIN).
-- 순환이 없는지는 넣기 전에 사람이 확인했다 — 모두 "기초 → 응용" 한 방향이다.

SET NAMES utf8mb4;

INSERT INTO SKILL_PREREQUISITE (skill_id, prereq_skill_id)
SELECT s.id, p.id
FROM (
    -- 뒤에 올 기술          먼저 할 기술
    SELECT 'Spring'       AS skill, 'Java'       AS prereq
    UNION ALL SELECT 'Spring Boot',   'Spring'
    UNION ALL SELECT 'JPA',           'SQL'          -- 시드에 JPA가 없으면 이 줄은 건너뛴다
    UNION ALL SELECT 'MySQL',         'SQL'
    UNION ALL SELECT 'Redis',         'SQL'
    UNION ALL SELECT 'Docker',        'Linux'
    UNION ALL SELECT 'Kubernetes',    'Docker'
    UNION ALL SELECT 'Terraform',     'AWS'
    UNION ALL SELECT 'Jenkins',       'Git'
    UNION ALL SELECT 'Nginx',         'Linux'
    UNION ALL SELECT 'React',         'JavaScript'
    UNION ALL SELECT 'Vue.js',        'JavaScript'
    UNION ALL SELECT 'Node.js',       'JavaScript'
    UNION ALL SELECT 'TypeScript',    'JavaScript'
    UNION ALL SELECT 'Pandas',        'Python'
    UNION ALL SELECT 'NumPy',         'Python'
    UNION ALL SELECT 'Django',        'Python'
    UNION ALL SELECT 'Flask',         'Python'
    UNION ALL SELECT 'TensorFlow',    'Python'
    UNION ALL SELECT 'PyTorch',       'Python'
    UNION ALL SELECT 'Spark',         'Hadoop'
) pairs
JOIN SKILL s ON s.skill_name = pairs.skill  AND s.is_deleted = FALSE
JOIN SKILL p ON p.skill_name = pairs.prereq AND p.is_deleted = FALSE
ON DUPLICATE KEY UPDATE SKILL_PREREQUISITE.is_deleted = FALSE;
