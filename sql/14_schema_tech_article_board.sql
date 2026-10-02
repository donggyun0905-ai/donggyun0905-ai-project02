-- 스펙 오디세이 (Spec Odyssey) — 기술 글 게시판 (TECH_ARTICLE + 댓글·하트·북마크·조회·신고)
-- 대상: sql/03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03_schema_extended.sql에는 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "기술 글 게시판" (개발일지 4-3 — EXPERT 증빙을 "블로그처럼 공개")
-- 주의: 화면·서비스·DAO는 팀 회의 뒤에 만든다. 이 파일은 스키마만 선점한다.

SET NAMES utf8mb4;

-- =========================================================
-- TECH_ARTICLE (기술 글 — 게시판) — 신설
-- 로드맵 EXPERT 단계의 "기술 설명 글"을 서비스 안에서 블로그처럼 공개한다 (개발일지 4-3).
-- 규칙 판정(글자 수·키워드·링크)을 통과하면 곧바로 PUBLISHED, 문제가 있으면 팀이 나중에 HIDDEN으로 내린다.
-- 댓글·하트·북마크·조회수가 붙는 게시판이라서 아래 TECH_ARTICLE_* 5개 테이블이 이 글에 매달린다.
-- roadmap_step_id UNIQUE: EXPERT 단계 하나에서 글 하나. NULL(자유 글)은 여러 개 허용된다.
-- *_count 컬럼은 목록·정렬을 위한 집계값이다. 하트·댓글·북마크·조회가 일어나는 같은 트랜잭션에서 함께 갱신한다.
-- =========================================================
CREATE TABLE TECH_ARTICLE (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    skill_id         BIGINT       NOT NULL, -- 어떤 기술에 대한 글인지 — 다른 사용자가 이 기준으로 찾아본다
    roadmap_step_id  BIGINT       NULL,     -- EXPERT 단계에서 나온 글이면 그 단계. 자유 글이면 NULL
    source_type      VARCHAR(20)  NOT NULL DEFAULT 'ROADMAP_EXPERT', -- ROADMAP_EXPERT(로드맵 증빙) / FREE(자유 작성)
    title            VARCHAR(200) NOT NULL,
    content          TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT(임시저장) / PUBLISHED(공개) / HIDDEN(운영자가 내림)
    published_at     DATETIME     NULL,
    hidden_reason    VARCHAR(200) NULL,     -- 내린 이유 — 작성자에게 보여준다
    hidden_at        DATETIME     NULL,
    view_count       INT          NOT NULL DEFAULT 0,
    like_count       INT          NOT NULL DEFAULT 0,
    comment_count    INT          NOT NULL DEFAULT 0,
    bookmark_count   INT          NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_roadmap_step (roadmap_step_id),
    KEY idx_tech_article_user_id (user_id),
    KEY idx_tech_article_skill_status (skill_id, status, published_at),
    KEY idx_tech_article_status_published (status, published_at),
    CONSTRAINT fk_tech_article_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_roadmap_step
        FOREIGN KEY (roadmap_step_id) REFERENCES ROADMAP_STEP (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_COMMENT (기술 글 댓글) — 신설
-- 대댓글은 한 단계만 허용한다(parent_comment_id가 가리키는 댓글은 최상위여야 함 — 애플리케이션 규칙).
-- 삭제는 is_deleted로 하고, 대댓글이 달린 댓글은 화면에 "삭제된 댓글입니다"로 남긴다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_COMMENT (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    article_id          BIGINT         NOT NULL,
    user_id             BIGINT         NOT NULL,
    parent_comment_id   BIGINT         NULL, -- 대댓글이면 부모 댓글 (자기참조)
    content             VARCHAR(1000)  NOT NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_tech_article_comment_article_id (article_id, created_at),
    KEY idx_tech_article_comment_user_id (user_id),
    KEY idx_tech_article_comment_parent_id (parent_comment_id),
    CONSTRAINT fk_tech_article_comment_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_comment_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES TECH_ARTICLE_COMMENT (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_LIKE (기술 글 하트) — 신설
-- 복합 UNIQUE: (article_id, user_id) — 한 사람이 한 글에 하트는 한 번.
-- 취소는 is_deleted = TRUE, 다시 누르면 같은 행을 되살린다(EVALUATION_SESSION_ITEM과 같은 방식).
-- =========================================================
CREATE TABLE TECH_ARTICLE_LIKE (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    article_id   BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_like_article_user (article_id, user_id),
    KEY idx_tech_article_like_user_id (user_id),
    CONSTRAINT fk_tech_article_like_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_like_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_BOOKMARK (기술 글 북마크) — 신설
-- 복합 UNIQUE: (article_id, user_id). 취소·재등록 방식은 TECH_ARTICLE_LIKE와 같다.
-- 내 북마크 목록은 user_id 인덱스로 읽는다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_BOOKMARK (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    article_id   BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_bookmark_article_user (article_id, user_id),
    KEY idx_tech_article_bookmark_user_id (user_id, created_at),
    CONSTRAINT fk_tech_article_bookmark_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_bookmark_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_VIEW_LOG (기술 글 조회 이력) — 신설
-- 조회수를 새로고침마다 올리지 않으려고 "사용자 1명 × 글 1개 × 하루 1회"만 센다.
-- 복합 UNIQUE: (article_id, viewer_user_id, viewed_date) — 이미 있으면 TECH_ARTICLE.view_count를 올리지 않는다.
-- 작성자 본인의 조회는 세지 않는다(애플리케이션 규칙).
-- =========================================================
CREATE TABLE TECH_ARTICLE_VIEW_LOG (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    article_id       BIGINT   NOT NULL,
    viewer_user_id   BIGINT   NOT NULL,
    viewed_date      DATE     NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_view_log_article_viewer_date (article_id, viewer_user_id, viewed_date),
    KEY idx_tech_article_view_log_viewer_user_id (viewer_user_id),
    CONSTRAINT fk_tech_article_view_log_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_view_log_viewer
        FOREIGN KEY (viewer_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- TECH_ARTICLE_REPORT (기술 글 신고) — 신설
-- 자동 게시 + 사후 관리(개발일지 4-3)에서 "문제 있는 글을 팀이 나중에 내린다"의 입력 창구.
-- 복합 UNIQUE: (article_id, reporter_user_id) — 한 사람이 같은 글을 여러 번 신고해 건수를 부풀리지 못하게.
-- 처리 결과로 글을 내리면 TECH_ARTICLE.status = HIDDEN, 이 행은 ACTION_TAKEN으로 바꾼다.
-- =========================================================
CREATE TABLE TECH_ARTICLE_REPORT (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    article_id         BIGINT        NOT NULL,
    reporter_user_id   BIGINT        NOT NULL,
    reason_type        VARCHAR(20)   NOT NULL, -- SPAM(광고) / ABUSE(비방) / COPYRIGHT(무단 복제) / INACCURATE(잘못된 내용) / OTHER
    detail             VARCHAR(500)  NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN', -- OPEN(대기) / ACTION_TAKEN(글을 내림) / DISMISSED(문제 없음)
    handled_at         DATETIME      NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_report_article_reporter (article_id, reporter_user_id),
    KEY idx_tech_article_report_status (status, created_at),
    KEY idx_tech_article_report_reporter_user_id (reporter_user_id),
    CONSTRAINT fk_tech_article_report_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_tech_article_report_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
