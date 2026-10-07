-- 스펙 오디세이 (Spec Odyssey) — 면접관 뷰 보강 (2026-10-07)
-- 대상: 28번까지 실행한 DB. 한 번만 실행한다.
--       (01_schema.sql·03_schema_extended.sql에도 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "USER_SPECS", "USER_PROJECTS", "USER_EDUCATION", "SHARE_LINK", "EVALUATION_SESSION_ITEM"
--
-- 면접관이 서류에서 실제로 보는 것(기여도·경험·학력)과 검토 작업 흐름을 채운다. 모두 NULL 허용 또는 기본값이 있어
-- 기존 코드·데이터는 그대로 동작한다.
--   1) USER_SPECS.end_date            : 경험(EXPERIENCE — 인턴·대외활동·교육)의 종료일. acquired_date가 시작일
--   2) USER_PROJECTS.team_size·my_role : 팀 인원(본인 포함)과 본인 역할
--   3) USER_EDUCATION                 : 학력(학교·재학 상태·졸업일·학점) — 계정당 한 줄
--   4) SHARE_LINK.scope_project_docs  : 프로젝트 제출 서류 파일 공개 (기본 비공개)
--      SHARE_LINK.scope_education     : 학력 공개 (기본 비공개 — 블라인드 채용)
--   5) EVALUATION_SESSION_ITEM.review_status·rating·memo : 면접관 본인의 검토 상태·평점·메모

SET NAMES utf8mb4;

ALTER TABLE USER_SPECS
    ADD COLUMN end_date DATE NULL COMMENT '경험(EXPERIENCE)의 종료일 — 진행 중이면 NULL' AFTER acquired_date;

ALTER TABLE USER_PROJECTS
    ADD COLUMN team_size INT          NULL COMMENT '팀 인원(본인 포함). 1이면 개인 프로젝트' AFTER retrospective,
    ADD COLUMN my_role   VARCHAR(100) NULL COMMENT '본인 역할 — 예) 백엔드 API · DB 설계' AFTER team_size;

CREATE TABLE USER_EDUCATION (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    school_name        VARCHAR(100)  NOT NULL,
    graduation_status  VARCHAR(20)   NOT NULL,        -- ENROLLED(재학) / LEAVE(휴학) / EXPECTED(졸업 예정) / GRADUATED(졸업)
    graduation_date    DATE          NULL,            -- 졸업일 또는 졸업 예정일
    gpa                DECIMAL(3,2)  NULL,
    gpa_max            DECIMAL(3,2)  NULL,            -- 4.5 / 4.3 / 4.0 — 학점을 넣으면 같이 넣는다
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_education_user (user_id),
    CONSTRAINT fk_user_education_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE SHARE_LINK
    ADD COLUMN scope_project_docs BOOLEAN NOT NULL DEFAULT FALSE AFTER scope_age,
    ADD COLUMN scope_education    BOOLEAN NOT NULL DEFAULT FALSE AFTER scope_project_docs;

ALTER TABLE EVALUATION_SESSION_ITEM
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'REVIEWING' COMMENT 'REVIEWING / PASS / HOLD / FAIL' AFTER added_at,
    ADD COLUMN rating        TINYINT     NULL COMMENT '면접관 평점 1~5' AFTER review_status,
    ADD COLUMN memo          TEXT        NULL COMMENT '면접관 메모 — 지원자에게 보이지 않음' AFTER rating;
