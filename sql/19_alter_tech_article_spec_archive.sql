-- 스펙 오디세이 (Spec Odyssey) — 스펙 아카이브 (상위 티어 팁 게시판)
-- 대상: 14_schema_tech_article_board.sql(또는 그 내용이 들어간 03_schema_extended.sql)로
--       TECH_ARTICLE 게시판 테이블을 이미 만든 DB에만 실행한다. 한 번만 실행한다.
--       (03_schema_extended.sql에도 같은 내용이 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "기술 글 게시판" — 스펙 아카이브
--
-- 스펙 아카이브는 TECH_ARTICLE을 그대로 쓰고 source_type = 'ARCHIVE_TIP'으로 구분한다.
--   - 글쓰기: 상위 2개 티어(개척자·오디세이아)만 / 읽기·댓글·하트·북마크: 로그인한 누구나 (애플리케이션 규칙)
--   - 제목 30자, 본문 2000자, 댓글 300자 (애플리케이션 규칙 — 기존 컬럼 크기 안이라 컬럼 변경 없음)
--   - 첨부(업로드 이미지 · 이미지 링크 · 유튜브)는 개수 제한 없음 — 용량만 본다(글 하나에 사진을 모두 합쳐 10MB)
--   - 첨부 위치는 본문의 [[att:N]] 표시(N = sort_order)로 정한다. 본문에 적은 유튜브·이미지 링크는 자동으로 첨부가 된다

SET NAMES utf8mb4;

-- =========================================================
-- 1) TECH_ARTICLE — 팁 글은 특정 기술에 매이지 않으므로 skill_id를 NULL 허용으로 바꾼다.
--    EXPERT 증빙 글(ROADMAP_EXPERT)은 계속 skill_id를 채운다 (애플리케이션 규칙).
--    목록 화면(아카이브 글만 최신순)용 인덱스를 추가한다.
-- =========================================================
ALTER TABLE TECH_ARTICLE
    MODIFY COLUMN skill_id BIGINT NULL COMMENT '어떤 기술에 대한 글인지. ARCHIVE_TIP(스펙 아카이브)은 NULL 가능',
    MODIFY COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'ROADMAP_EXPERT'
        COMMENT 'ROADMAP_EXPERT(로드맵 증빙) / FREE(자유 작성) / ARCHIVE_TIP(스펙 아카이브 팁)',
    ADD KEY idx_tech_article_source_status_published (source_type, status, published_at);

-- =========================================================
-- 2) TECH_ARTICLE_COMMENT — 인스타그램식 답글.
--    답글은 모두 최상위 댓글(parent_comment_id) 밑에 한 줄기로 모이고,
--    답글에 다시 답하면 그 사람을 reply_to_user_id에 남겨 화면에 "@이름"으로 보여준다.
-- =========================================================
ALTER TABLE TECH_ARTICLE_COMMENT
    ADD COLUMN reply_to_user_id BIGINT NULL COMMENT '답글이 가리키는 사람 — 화면에 @이름으로 표시. 최상위 댓글이면 NULL'
        AFTER parent_comment_id,
    ADD KEY idx_tech_article_comment_reply_to_user_id (reply_to_user_id),
    ADD CONSTRAINT fk_tech_article_comment_reply_to_user
        FOREIGN KEY (reply_to_user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- =========================================================
-- 3) TECH_ARTICLE_ATTACHMENT (글 첨부) — 신설
--    글 하나에 여러 개. attachment_type에 따라 쓰는 컬럼이 다르다.
--      IMAGE_UPLOAD : 서버에 저장한 이미지 → original_name · stored_name · file_path · file_size · mime_type
--      IMAGE_URL    : 외부 이미지 링크(https) → url
--      YOUTUBE      : 유튜브 영상 → url(원본 링크) · embed_key(영상 ID 11자리, 임베드할 때 이걸로만 주소를 만든다)
--    url은 길 수 있어 VARCHAR(2048)로 따로 둔다 (브라우저가 실제로 다루는 URL 길이 상한).
--    복합 UNIQUE: (article_id, sort_order) — 한 글 안에서 표시 순서가 겹치지 않게.
-- =========================================================
CREATE TABLE TECH_ARTICLE_ATTACHMENT (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    article_id       BIGINT         NOT NULL,
    attachment_type  VARCHAR(20)    NOT NULL, -- IMAGE_UPLOAD / IMAGE_URL / YOUTUBE
    sort_order       INT            NOT NULL, -- 0부터, 글에 보이는 순서
    url              VARCHAR(2048)  NULL,     -- IMAGE_URL · YOUTUBE 원본 링크
    embed_key        VARCHAR(20)    NULL,     -- YOUTUBE 영상 ID
    original_name    VARCHAR(255)   NULL,     -- IMAGE_UPLOAD 원본 파일명 (화면 표시용)
    stored_name      VARCHAR(255)   NULL,     -- IMAGE_UPLOAD 저장 파일명 (중복 방지)
    file_path        VARCHAR(500)   NULL,     -- IMAGE_UPLOAD 저장 경로
    file_size        BIGINT         NULL,     -- IMAGE_UPLOAD 바이트 (10MB 제한 검증)
    mime_type        VARCHAR(100)   NULL,     -- IMAGE_UPLOAD image/png · image/jpeg · image/gif · image/webp
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tech_article_attachment_article_order (article_id, sort_order),
    CONSTRAINT fk_tech_article_attachment_article
        FOREIGN KEY (article_id) REFERENCES TECH_ARTICLE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
