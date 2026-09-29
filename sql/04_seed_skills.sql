-- 스펙 오디세이 (Spec Odyssey) — SKILL / JOB_REQUIRED_SKILL / JOB_ALIAS 초기 데이터
-- 대상: SKILL(IT 기술 표준 명칭), JOB_REQUIRED_SKILL(18개 직무별 요구 기술), JOB_ALIAS(직무별 별칭)
-- 전제 조건: 01_schema.sql, 02_seed.sql(JOB 18건), 03_schema_extended.sql(JOB_REQUIRED_SKILL 테이블) 선행 실행
-- 기준 문서: docs/db-design.md
-- 매칭 방식: JOB/SKILL은 AUTO_INCREMENT id를 하드코딩하지 않고 job_name / skill_name으로 서브쿼리 조인한다.
-- JOB_REQUIRED_SKILL.source = 'MANUAL', is_estimated = TRUE, collected_at = NULL
--   → 아직 워크넷(WORKNET) 실수집 전 단계이므로, 운영진이 사전 정의한 추정치임을 명시한다.
--   (TD-2 On-demand 수집 배치가 돌면 source='WORKNET'/'LLM' 행으로 대체·보강될 수 있다.)

SET NAMES utf8mb4;

-- =========================================================
-- SKILL — IT 기술 표준 명칭 마스터 (163개)
-- embedding_vector / embedding_model / embedded_at 은 TD-1 임베딩 배치가 추후 채운다.
-- =========================================================
INSERT INTO SKILL (skill_name, category) VALUES
    ('Java', '프로그래밍 언어'),
    ('Python', '프로그래밍 언어'),
    ('JavaScript', '프로그래밍 언어'),
    ('TypeScript', '프로그래밍 언어'),
    ('Go', '프로그래밍 언어'),
    ('Kotlin', '프로그래밍 언어'),
    ('Swift', '프로그래밍 언어'),
    ('C++', '프로그래밍 언어'),
    ('C#', '프로그래밍 언어'),
    ('C', '프로그래밍 언어'),
    ('Rust', '프로그래밍 언어'),
    ('PHP', '프로그래밍 언어'),
    ('Ruby', '프로그래밍 언어'),
    ('Scala', '프로그래밍 언어'),
    ('R', '프로그래밍 언어'),
    ('SQL', '프로그래밍 언어'),
    ('Objective-C', '프로그래밍 언어'),
    ('Dart', '프로그래밍 언어'),
    ('Shell Script', '프로그래밍 언어'),
    ('Perl', '프로그래밍 언어'),
    ('Spring', '백엔드 프레임워크/런타임'),
    ('Spring Boot', '백엔드 프레임워크/런타임'),
    ('Django', '백엔드 프레임워크/런타임'),
    ('Flask', '백엔드 프레임워크/런타임'),
    ('FastAPI', '백엔드 프레임워크/런타임'),
    ('Node.js', '백엔드 프레임워크/런타임'),
    ('Express.js', '백엔드 프레임워크/런타임'),
    ('NestJS', '백엔드 프레임워크/런타임'),
    ('Ruby on Rails', '백엔드 프레임워크/런타임'),
    ('Laravel', '백엔드 프레임워크/런타임'),
    ('.NET', '백엔드 프레임워크/런타임'),
    ('ASP.NET Core', '백엔드 프레임워크/런타임'),
    ('React', '프론트엔드 프레임워크/라이브러리'),
    ('Vue.js', '프론트엔드 프레임워크/라이브러리'),
    ('Angular', '프론트엔드 프레임워크/라이브러리'),
    ('Next.js', '프론트엔드 프레임워크/라이브러리'),
    ('Nuxt.js', '프론트엔드 프레임워크/라이브러리'),
    ('Svelte', '프론트엔드 프레임워크/라이브러리'),
    ('jQuery', '프론트엔드 프레임워크/라이브러리'),
    ('Redux', '프론트엔드 프레임워크/라이브러리'),
    ('HTML5', '프론트엔드 프레임워크/라이브러리'),
    ('CSS3', '프론트엔드 프레임워크/라이브러리'),
    ('Sass', '프론트엔드 프레임워크/라이브러리'),
    ('Tailwind CSS', '프론트엔드 프레임워크/라이브러리'),
    ('Webpack', '프론트엔드 프레임워크/라이브러리'),
    ('Vite', '프론트엔드 프레임워크/라이브러리'),
    ('Android SDK', '모바일 개발'),
    ('Jetpack Compose', '모바일 개발'),
    ('SwiftUI', '모바일 개발'),
    ('UIKit', '모바일 개발'),
    ('Xcode', '모바일 개발'),
    ('Android Studio', '모바일 개발'),
    ('React Native', '모바일 개발'),
    ('Flutter', '모바일 개발'),
    ('MySQL', '데이터베이스'),
    ('PostgreSQL', '데이터베이스'),
    ('Oracle Database', '데이터베이스'),
    ('MS SQL Server', '데이터베이스'),
    ('MariaDB', '데이터베이스'),
    ('SQLite', '데이터베이스'),
    ('MongoDB', '데이터베이스'),
    ('Redis', '데이터베이스'),
    ('Cassandra', '데이터베이스'),
    ('DynamoDB', '데이터베이스'),
    ('Elasticsearch', '데이터베이스'),
    ('Neo4j', '데이터베이스'),
    ('Amazon RDS', '데이터베이스'),
    ('AWS', '클라우드/인프라'),
    ('Google Cloud Platform', '클라우드/인프라'),
    ('Microsoft Azure', '클라우드/인프라'),
    ('Docker', '클라우드/인프라'),
    ('Kubernetes', '클라우드/인프라'),
    ('Terraform', '클라우드/인프라'),
    ('Ansible', '클라우드/인프라'),
    ('Jenkins', '클라우드/인프라'),
    ('GitLab CI/CD', '클라우드/인프라'),
    ('GitHub Actions', '클라우드/인프라'),
    ('Helm', '클라우드/인프라'),
    ('Nginx', '클라우드/인프라'),
    ('Apache HTTP Server', '클라우드/인프라'),
    ('Prometheus', '클라우드/인프라'),
    ('Grafana', '클라우드/인프라'),
    ('ELK Stack', '클라우드/인프라'),
    ('CloudFormation', '클라우드/인프라'),
    ('Vault', '클라우드/인프라'),
    ('Load Balancing', '클라우드/인프라'),
    ('Apache Spark', '데이터 엔지니어링/빅데이터'),
    ('Apache Kafka', '데이터 엔지니어링/빅데이터'),
    ('Apache Airflow', '데이터 엔지니어링/빅데이터'),
    ('Hadoop', '데이터 엔지니어링/빅데이터'),
    ('Hive', '데이터 엔지니어링/빅데이터'),
    ('Apache Flink', '데이터 엔지니어링/빅데이터'),
    ('dbt', '데이터 엔지니어링/빅데이터'),
    ('Snowflake', '데이터 엔지니어링/빅데이터'),
    ('Google BigQuery', '데이터 엔지니어링/빅데이터'),
    ('Amazon Redshift', '데이터 엔지니어링/빅데이터'),
    ('Apache NiFi', '데이터 엔지니어링/빅데이터'),
    ('TensorFlow', '머신러닝/AI'),
    ('PyTorch', '머신러닝/AI'),
    ('scikit-learn', '머신러닝/AI'),
    ('Keras', '머신러닝/AI'),
    ('Pandas', '머신러닝/AI'),
    ('NumPy', '머신러닝/AI'),
    ('XGBoost', '머신러닝/AI'),
    ('Jupyter Notebook', '머신러닝/AI'),
    ('MLflow', '머신러닝/AI'),
    ('Hugging Face Transformers', '머신러닝/AI'),
    ('Matplotlib', '머신러닝/AI'),
    ('Selenium', 'QA/테스트'),
    ('Appium', 'QA/테스트'),
    ('JUnit', 'QA/테스트'),
    ('pytest', 'QA/테스트'),
    ('Cypress', 'QA/테스트'),
    ('Postman', 'QA/테스트'),
    ('Apache JMeter', 'QA/테스트'),
    ('TestNG', 'QA/테스트'),
    ('Playwright', 'QA/테스트'),
    ('Wireshark', '보안'),
    ('Metasploit', '보안'),
    ('Burp Suite', '보안'),
    ('Nessus', '보안'),
    ('OAuth 2.0', '보안'),
    ('PKI', '보안'),
    ('SIEM', '보안'),
    ('IAM', '보안'),
    ('Nmap', '보안'),
    ('Snort', '보안'),
    ('Kali Linux', '보안'),
    ('Linux', '네트워크/시스템'),
    ('Windows Server', '네트워크/시스템'),
    ('TCP/IP', '네트워크/시스템'),
    ('DNS', '네트워크/시스템'),
    ('VPN', '네트워크/시스템'),
    ('Cisco IOS', '네트워크/시스템'),
    ('BGP', '네트워크/시스템'),
    ('Bash', '네트워크/시스템'),
    ('PowerShell', '네트워크/시스템'),
    ('VMware', '네트워크/시스템'),
    ('Zabbix', '네트워크/시스템'),
    ('Git', '협업/방법론/기타'),
    ('GitHub', '협업/방법론/기타'),
    ('GitLab', '협업/방법론/기타'),
    ('Jira', '협업/방법론/기타'),
    ('Confluence', '협업/방법론/기타'),
    ('GraphQL', '협업/방법론/기타'),
    ('REST API', '협업/방법론/기타'),
    ('gRPC', '협업/방법론/기타'),
    ('Microservices Architecture', '협업/방법론/기타'),
    ('RabbitMQ', '협업/방법론/기타'),
    ('WebSocket', '협업/방법론/기타'),
    ('Design Patterns', '협업/방법론/기타'),
    ('Agile/Scrum', '협업/방법론/기타'),
    ('CI/CD', '협업/방법론/기타'),
    ('TDD', '협업/방법론/기타'),
    ('Unit Testing', '협업/방법론/기타'),
    ('Figma', '협업/방법론/기타'),
    ('Sketch', '협업/방법론/기타'),
    ('Adobe XD', '협업/방법론/기타'),
    ('Zeplin', '협업/방법론/기타'),
    ('UI/UX Design', '협업/방법론/기타'),
    ('Wireframing', '협업/방법론/기타'),
    ('Prototyping', '협업/방법론/기타'),
    ('Design System', '협업/방법론/기타');

-- =========================================================
-- JOB_REQUIRED_SKILL — 직무별 요구 기술 (18개 직무 × 10~15개, 총 202건)
-- importance: REQUIRED(필수) / PREFERRED(우대)
-- required_level: BASIC / INTERMEDIATE / ADVANCED
-- =========================================================
INSERT INTO JOB_REQUIRED_SKILL
    (job_id, skill_id, importance, required_level, source, is_estimated, collected_at)
SELECT j.id, s.id, x.importance, x.required_level, 'MANUAL', TRUE, NULL
FROM (
    SELECT '백엔드 개발자' AS job_name, 'Java' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Spring Boot' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Python' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'MySQL' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'PostgreSQL' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Redis' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'REST API' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Docker' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Microservices Architecture' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'Kubernetes' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, 'SQL' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Java' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Spring' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'C++' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'TCP/IP' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'MySQL' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Redis' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'REST API' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Nginx' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Docker' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서버 개발자' AS job_name, 'Bash' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Java' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Spring Boot' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Node.js' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Express.js' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'REST API' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'GraphQL' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'gRPC' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'MySQL' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Redis' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Docker' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'Postman' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'JavaScript' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'TypeScript' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'React' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Vue.js' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'HTML5' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'CSS3' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Sass' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Tailwind CSS' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Webpack' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Vite' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, 'REST API' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'HTML5' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'CSS3' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Sass' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'JavaScript' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'jQuery' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Figma' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Adobe XD' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Zeplin' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Webpack' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'Design System' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'HTML5' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'CSS3' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'JavaScript' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'TypeScript' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'React' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Vue.js' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Figma' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Sass' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Tailwind CSS' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Design System' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Python' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'SQL' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Apache Spark' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Apache Kafka' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Apache Airflow' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Hadoop' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Hive' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Amazon Redshift' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Google BigQuery' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'dbt' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Docker' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'AWS' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, 'Git' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'SQL' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Python' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'R' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Pandas' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Matplotlib' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Google BigQuery' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Amazon Redshift' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Jupyter Notebook' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Git' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'Snowflake' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'Python' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'R' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'SQL' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'Pandas' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'NumPy' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'scikit-learn' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'TensorFlow' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'PyTorch' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'Jupyter Notebook' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'Matplotlib' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'XGBoost' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'MLflow' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Docker' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Kubernetes' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Terraform' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Ansible' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Jenkins' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'GitLab CI/CD' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'GitHub Actions' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'AWS' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Prometheus' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Grafana' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'Helm' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, 'CI/CD' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'AWS' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Google Cloud Platform' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Microsoft Azure' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Terraform' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'CloudFormation' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Docker' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Kubernetes' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Load Balancing' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'IAM' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, 'Vault' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Windows Server' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'VMware' AS skill_name, 'PREFERRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Bash' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'PowerShell' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Zabbix' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Nginx' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Apache HTTP Server' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'TCP/IP' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'AWS' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'Ansible' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'SIEM' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'IAM' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'OAuth 2.0' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'PKI' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Nmap' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Wireshark' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Vault' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Nessus' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'Bash' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Wireshark' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Metasploit' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Burp Suite' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Nessus' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'OAuth 2.0' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'PKI' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'SIEM' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'IAM' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Nmap' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Kali Linux' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, 'Vault' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Metasploit' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Burp Suite' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Nmap' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Kali Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Wireshark' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Nessus' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Snort' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Linux' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Bash' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, 'Python' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Jira' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Confluence' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Figma' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'SQL' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Agile/Scrum' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Wireframing' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Prototyping' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'REST API' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'Git' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'UI/UX Design' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Figma' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Jira' AS skill_name, 'REQUIRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Confluence' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Wireframing' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Prototyping' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'UI/UX Design' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Agile/Scrum' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'SQL' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Design System' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '서비스 기획자' AS job_name, 'Adobe XD' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Jira' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Confluence' AS skill_name, 'REQUIRED' AS importance, 'INTERMEDIATE' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Agile/Scrum' AS skill_name, 'REQUIRED' AS importance, 'ADVANCED' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Git' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'CI/CD' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'SQL' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'REST API' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Design Patterns' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'Unit Testing' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'TDD' AS skill_name, 'PREFERRED' AS importance, 'BASIC' AS required_level
) x
JOIN JOB j ON j.job_name = x.job_name
JOIN SKILL s ON s.skill_name = x.skill_name;

-- =========================================================
-- JOB_ALIAS — 직무명 별칭 (18개 직무 × 3~5개, 총 54건)
-- match_type: 컬럼 기본값 'MANUAL' 사용, similarity_score는 수기 등록이므로 NULL
-- =========================================================
INSERT INTO JOB_ALIAS (job_id, alias_name)
SELECT j.id, x.alias_name
FROM (
    SELECT '백엔드 개발자' AS job_name, '백엔드 엔지니어' AS alias_name
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, '서버사이드 개발자' AS alias_name
    UNION ALL
    SELECT '백엔드 개발자' AS job_name, '백엔드 소프트웨어 엔지니어' AS alias_name
    UNION ALL
    SELECT '서버 개발자' AS job_name, '서버 엔지니어' AS alias_name
    UNION ALL
    SELECT '서버 개발자' AS job_name, '서버사이드 엔지니어' AS alias_name
    UNION ALL
    SELECT '서버 개발자' AS job_name, '백엔드 서버 개발자' AS alias_name
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'API 엔지니어' AS alias_name
    UNION ALL
    SELECT 'API 개발자' AS job_name, '백엔드 API 개발자' AS alias_name
    UNION ALL
    SELECT 'API 개발자' AS job_name, 'API 서비스 개발자' AS alias_name
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, '프론트엔드 엔지니어' AS alias_name
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, '웹 프론트엔드 개발자' AS alias_name
    UNION ALL
    SELECT '프론트엔드 개발자' AS job_name, '프론트엔드 소프트웨어 엔지니어' AS alias_name
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, '퍼블리셔' AS alias_name
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, 'HTML 퍼블리셔' AS alias_name
    UNION ALL
    SELECT '웹퍼블리셔' AS job_name, '마크업 개발자' AS alias_name
    UNION ALL
    SELECT 'UI 개발자' AS job_name, 'UI 엔지니어' AS alias_name
    UNION ALL
    SELECT 'UI 개발자' AS job_name, '사용자 인터페이스 개발자' AS alias_name
    UNION ALL
    SELECT 'UI 개발자' AS job_name, '웹 UI 개발자' AS alias_name
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, '빅데이터 엔지니어' AS alias_name
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, '데이터 파이프라인 엔지니어' AS alias_name
    UNION ALL
    SELECT '데이터 엔지니어' AS job_name, '데이터 플랫폼 엔지니어' AS alias_name
    UNION ALL
    SELECT '데이터 분석가' AS job_name, '비즈니스 분석가' AS alias_name
    UNION ALL
    SELECT '데이터 분석가' AS job_name, 'BI 분석가' AS alias_name
    UNION ALL
    SELECT '데이터 분석가' AS job_name, '데이터 애널리스트' AS alias_name
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, 'AI 데이터 사이언티스트' AS alias_name
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, '데이터 과학자' AS alias_name
    UNION ALL
    SELECT '데이터 사이언티스트' AS job_name, '머신러닝 데이터 분석가' AS alias_name
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, '데브옵스 엔지니어' AS alias_name
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, '인프라 자동화 엔지니어' AS alias_name
    UNION ALL
    SELECT 'DevOps 엔지니어' AS job_name, '운영 자동화 엔지니어' AS alias_name
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, '클라우드 아키텍트' AS alias_name
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, '클라우드 인프라 엔지니어' AS alias_name
    UNION ALL
    SELECT '클라우드 엔지니어' AS job_name, '클라우드 운영 엔지니어' AS alias_name
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, '인프라 엔지니어' AS alias_name
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, '서버 운영 엔지니어' AS alias_name
    UNION ALL
    SELECT '시스템 엔지니어' AS job_name, 'IT 인프라 엔지니어' AS alias_name
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, '보안 담당자' AS alias_name
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, 'ISMS 담당자' AS alias_name
    UNION ALL
    SELECT '정보보안 담당자' AS job_name, '정보보호 담당자' AS alias_name
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, '사이버보안 엔지니어' AS alias_name
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, '정보보안 엔지니어' AS alias_name
    UNION ALL
    SELECT '보안 엔지니어' AS job_name, '시큐리티 엔지니어' AS alias_name
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, '침투테스터' AS alias_name
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, '화이트해커' AS alias_name
    UNION ALL
    SELECT '모의해킹 전문가' AS job_name, '모의침투 전문가' AS alias_name
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'IT 서비스 기획자' AS alias_name
    UNION ALL
    SELECT 'IT 기획자' AS job_name, '기술 기획자' AS alias_name
    UNION ALL
    SELECT 'IT 기획자' AS job_name, 'IT 프로덕트 기획자' AS alias_name
    UNION ALL
    SELECT '서비스 기획자' AS job_name, '프로덕트 기획자' AS alias_name
    UNION ALL
    SELECT '서비스 기획자' AS job_name, '서비스 매니저' AS alias_name
    UNION ALL
    SELECT '서비스 기획자' AS job_name, '프로덕트 매니저' AS alias_name
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'PM' AS alias_name
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, '프로젝트 관리자' AS alias_name
    UNION ALL
    SELECT '프로젝트 매니저(PM)' AS job_name, 'IT PM' AS alias_name
) x
JOIN JOB j ON j.job_name = x.job_name;
