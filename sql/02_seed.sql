-- 스펙 오디세이 (Spec Odyssey) — 1주차 초기 데이터
-- 대상: JOB(IT 직무 18개), CERTIFICATION(IT 자격증 37개)
-- 기준 문서: docs/db-design.md (job_category: BACKEND/FRONTEND/DATA/DEVOPS/SECURITY/PM)

SET NAMES utf8mb4;

-- =========================================================
-- JOB — IT 직무 마스터 (18개)
-- is_popular = TRUE: 사전 수집 대상 (수요가 많아 미리 데이터를 갖춰야 하는 직무)
-- last_collected_at은 아직 워크넷 수집 전이므로 NULL
-- =========================================================
INSERT INTO JOB (job_name, job_category, is_popular, last_collected_at) VALUES
    ('백엔드 개발자',        'BACKEND',  TRUE,  NULL),
    ('서버 개발자',          'BACKEND',  FALSE, NULL),
    ('API 개발자',           'BACKEND',  TRUE,  NULL),
    ('프론트엔드 개발자',     'FRONTEND', TRUE,  NULL),
    ('웹퍼블리셔',           'FRONTEND', FALSE, NULL),
    ('UI 개발자',            'FRONTEND', TRUE,  NULL),
    ('데이터 엔지니어',       'DATA',     TRUE,  NULL),
    ('데이터 분석가',         'DATA',     TRUE,  NULL),
    ('데이터 사이언티스트',    'DATA',     TRUE,  NULL),
    ('DevOps 엔지니어',      'DEVOPS',   TRUE,  NULL),
    ('클라우드 엔지니어',     'DEVOPS',   FALSE, NULL),
    ('시스템 엔지니어',       'DEVOPS',   FALSE, NULL),
    ('정보보안 담당자',       'SECURITY', TRUE,  NULL),
    ('보안 엔지니어',         'SECURITY', FALSE, NULL),
    ('모의해킹 전문가',       'SECURITY', FALSE, NULL),
    ('IT 기획자',            'PM',       TRUE,  NULL),
    ('서비스 기획자',         'PM',       FALSE, NULL),
    ('프로젝트 매니저(PM)',   'PM',       FALSE, NULL);

-- =========================================================
-- CERTIFICATION — IT 자격증 마스터 (37개)
-- difficulty_level: 1(입문) ~ 5(전문가) — 로드맵 tier 배치 기준
-- =========================================================
INSERT INTO CERTIFICATION (cert_name, issuer, job_category, difficulty_level) VALUES
    ('정보처리기사',                              '한국산업인력공단',   'BACKEND',  3),
    ('정보처리산업기사',                           '한국산업인력공단',   'BACKEND',  2),
    ('정보처리기능사',                             '한국산업인력공단',   'BACKEND',  1),
    ('SQLD',                                     '한국데이터산업진흥원', 'DATA',     2),
    ('SQLP',                                     '한국데이터산업진흥원', 'DATA',     4),
    ('ADsP',                                     '한국데이터산업진흥원', 'DATA',     2),
    ('ADP',                                      '한국데이터산업진흥원', 'DATA',     4),
    ('빅데이터분석기사',                           '한국산업인력공단',   'DATA',     3),
    ('데이터아키텍처준전문가(DAsP)',                '한국데이터산업진흥원', 'DATA',     3),
    ('데이터아키텍처전문가(DAP)',                   '한국데이터산업진흥원', 'DATA',     4),
    ('리눅스마스터 1급',                           '한국정보통신진흥협회', 'DEVOPS',   3),
    ('리눅스마스터 2급',                           '한국정보통신진흥협회', 'DEVOPS',   2),
    ('네트워크관리사 1급',                         '한국정보통신자격협회', 'DEVOPS',   3),
    ('네트워크관리사 2급',                         '한국정보통신자격협회', 'DEVOPS',   2),
    ('정보보안기사',                              '한국산업인력공단',   'SECURITY', 3),
    ('정보보안산업기사',                           '한국산업인력공단',   'SECURITY', 2),
    ('정보시스템감리사',                           '한국정보화진흥원',   'PM',       5),
    ('CISA',                                     'ISACA',            'SECURITY', 4),
    ('CISSP',                                    '(ISC)²',           'SECURITY', 5),
    ('CCNA',                                     'Cisco',            'DEVOPS',   2),
    ('CCNP',                                     'Cisco',            'DEVOPS',   4),
    ('AWS Certified Cloud Practitioner',         'AWS',              'DEVOPS',   1),
    ('AWS Certified Solutions Architect - Associate',    'AWS',      'DEVOPS',   3),
    ('AWS Certified Solutions Architect - Professional', 'AWS',      'DEVOPS',   5),
    ('AWS Certified Developer - Associate',      'AWS',              'BACKEND',  3),
    ('AWS Certified SysOps Administrator - Associate',   'AWS',      'DEVOPS',   3),
    ('Microsoft Azure Fundamentals (AZ-900)',    'Microsoft',        'DEVOPS',   1),
    ('Microsoft Azure Administrator (AZ-104)',   'Microsoft',        'DEVOPS',   3),
    ('Google Cloud Associate Cloud Engineer',    'Google',           'DEVOPS',   3),
    ('Google Cloud Professional Data Engineer',  'Google',           'DATA',     4),
    ('CKA (Certified Kubernetes Administrator)', 'CNCF',             'DEVOPS',   4),
    ('Docker Certified Associate',               'Docker Inc.',      'DEVOPS',   3),
    ('OCJP (Oracle Certified Professional, Java SE Programmer)', 'Oracle', 'BACKEND', 3),
    ('컴퓨터활용능력 1급',                         '대한상공회의소',    'COMMON',   2),
    ('컴퓨터활용능력 2급',                         '대한상공회의소',    'COMMON',   1),
    ('PMP',                                      'PMI',              'PM',       4),
    ('ITIL Foundation',                          'AXELOS',           'PM',       2);
