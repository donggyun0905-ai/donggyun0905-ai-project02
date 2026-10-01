-- =========================================================
-- SKILL_ALIAS 영어 표기 보강 (2026-09-30 팀 결정 후속 — "영어도 올바르게 추출되게")
-- 10_seed_skill_alias.sql은 한글 표기 위주였다. 영어권에서도 원 벤더/프로젝트 접두사를
-- 빼고 줄여 부르는 표현(Postgres, Spark, Kafka 등)은 편집거리로 못 잡을 만큼 원래 이름과
-- 차이가 커서 별도 별칭이 필요하다.
-- 전제 조건: 09_schema_skill_alias.sql, 04_seed_skills.sql, 10_seed_skill_alias.sql 선행 실행
-- =========================================================

SET NAMES utf8mb4;

INSERT INTO SKILL_ALIAS (skill_id, alias_name)
SELECT s.id, x.alias_name
FROM (
    SELECT 'PostgreSQL' AS skill_name, 'Postgres' AS alias_name
    UNION ALL SELECT 'MongoDB', 'Mongo'
    UNION ALL SELECT 'Node.js', 'Node'
    UNION ALL SELECT 'Amazon Redshift', 'Redshift'
    UNION ALL SELECT 'Google BigQuery', 'BigQuery'
    UNION ALL SELECT 'Google Cloud Platform', 'Google Cloud'
    UNION ALL SELECT 'Microsoft Azure', 'Azure'
    UNION ALL SELECT 'Oracle Database', 'Oracle'
    UNION ALL SELECT 'MS SQL Server', 'MSSQL'
    UNION ALL SELECT 'Ruby on Rails', 'RoR'
    UNION ALL SELECT 'ASP.NET Core', 'ASP.NET'
    UNION ALL SELECT 'Elasticsearch', 'Elastic'
    UNION ALL SELECT 'Jupyter Notebook', 'Jupyter'
    UNION ALL SELECT 'Hugging Face Transformers', 'Hugging Face'
    UNION ALL SELECT 'Apache Spark', 'Spark'
    UNION ALL SELECT 'Apache Kafka', 'Kafka'
    UNION ALL SELECT 'Apache Airflow', 'Airflow'
    UNION ALL SELECT 'Apache Flink', 'Flink'
    UNION ALL SELECT 'Apache NiFi', 'NiFi'
    UNION ALL SELECT 'Apache JMeter', 'JMeter'
    UNION ALL SELECT 'Microservices Architecture', 'Microservices'
    UNION ALL SELECT 'Agile/Scrum', 'Agile'
    UNION ALL SELECT 'Agile/Scrum', 'Scrum'
    UNION ALL SELECT 'CI/CD', 'CICD'
) AS x
JOIN SKILL s ON s.skill_name = x.skill_name;
