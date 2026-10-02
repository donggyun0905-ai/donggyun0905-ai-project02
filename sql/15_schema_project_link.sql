-- 스펙 오디세이 (Spec Odyssey) — 프로젝트 기타 링크(PROJECT_LINK) 신설
-- 대상: sql/01_schema.sql · 03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03에는 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 선행: sql/13_alter_user_projects_docs.sql (USER_PROJECTS의 repo_url·deploy_url)
-- 기준 문서: docs/db-design.md "PROJECT_LINK"

-- =========================================================
-- PROJECT_LINK (프로젝트 기타 링크) — 신설
-- 저장소(repo_url)·배포(deploy_url) 말고도 블로그 글, 발표 영상, 노션 등 프로젝트를 보여줄 링크가 더 필요할 수 있어서
-- 이름(label) + 주소(url)를 프로젝트당 최대 5개까지 받는다. 면접관 공유 타임라인에도 같이 보인다.
-- 수정은 "기존 줄을 지우고(is_deleted) 새로 넣는" 방식이라 UNIQUE를 두지 않는다. sort_order로 입력 순서를 지킨다.
-- 주소는 http/https만 허용한다 — 애플리케이션이 저장할 때와 면접관 화면에 보여줄 때 둘 다 확인한다.
-- =========================================================
CREATE TABLE PROJECT_LINK (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    label         VARCHAR(50)  NULL,     -- 링크 이름(예: 블로그 글, 발표 영상). 비우면 화면에서 주소의 도메인을 보여준다
    url           VARCHAR(500) NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_project_link_project_id (project_id),
    CONSTRAINT fk_project_link_project
        FOREIGN KEY (project_id) REFERENCES USER_PROJECTS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
