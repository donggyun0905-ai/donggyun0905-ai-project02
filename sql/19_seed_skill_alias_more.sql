-- =========================================================
-- SKILL_ALIAS 보강 (2026-10-02, 매칭 정확도 평가에서 못 찾거나 잘못 연결된 표현)
-- 사람들이 보유 기술 칸에 흔히 쓰는데 이름·별칭·앞부분 일치·오타 허용으로 못 잡는 표현만 넣는다.
--   - 4글자 이하 약칭(REST, SCSS, MSA, Vue, Burp)은 오타 허용을 하지 않으므로 별칭이 필요하다
--   - 한글 표기 변형(셀레니움, C샵, 마리아DB, 래빗MQ, 다이나모DB), 업계 약칭(sklearn, Cpp)
--   - Cpp는 임베딩이 C로 잘못 잇고, 마리아DB·래빗MQ·다이나모DB는 임베딩 기준값(0.75) 경계라 별칭으로 확실히 잡는다
-- 다시 실행해도 안전하다: alias_name은 UNIQUE라 이미 있는 별칭은 INSERT IGNORE로 건너뛴다.
-- 전제 조건: 09_schema_skill_alias.sql, 04_seed_skills.sql 선행 실행
-- =========================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO SKILL_ALIAS (skill_id, alias_name)
SELECT s.id, x.alias_name
FROM (
    SELECT 'Vue.js' AS skill_name, 'Vue' AS alias_name
    UNION ALL SELECT 'scikit-learn', 'sklearn'
    UNION ALL SELECT 'Selenium', '셀레니움'
    UNION ALL SELECT 'Sass', 'SCSS'
    UNION ALL SELECT 'REST API', 'REST'
    UNION ALL SELECT 'REST API', 'RESTful'
    UNION ALL SELECT 'REST API', 'RESTful API'
    UNION ALL SELECT 'Microservices Architecture', 'MSA'
    UNION ALL SELECT 'C#', 'C샵'
    UNION ALL SELECT 'Burp Suite', 'Burp'
    UNION ALL SELECT 'C++', 'Cpp'
    UNION ALL SELECT 'MariaDB', '마리아DB'
    UNION ALL SELECT 'RabbitMQ', '래빗MQ'
    UNION ALL SELECT 'DynamoDB', '다이나모DB'
) AS x
JOIN SKILL s ON s.skill_name = x.skill_name AND s.is_deleted = FALSE;
