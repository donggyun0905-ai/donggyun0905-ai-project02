-- 데스크톱 캐릭터 설치 파일(Setup.exe) — DB에 보관해서 팀원 누구 서버에서든 같은 파일을 내려받고 업데이트한다
-- 대상: 32번까지 실행한 DB. 한 번만 실행한다. (03_schema_extended.sql에도 반영되어 있어 새 DB에서는 필요 없다)
-- 기준 문서: docs/desktop-companion-plan.md 11절
--
-- COMPANION_RELEASE       버전 하나 (버전·바뀐 점·크기·SHA-256). 관리자 화면에서 올린다.
-- COMPANION_RELEASE_CHUNK 파일 내용을 8MB씩 나눠 담는다 — 설치 파일이 약 47MB라 DB 한 번에 받는 상한(max_allowed_packet)을 넘기 때문.
-- 최근 2개 버전만 내용을 남긴다 (오래된 버전은 논리 삭제하고 내용을 비워 DB가 커지지 않게).

SET NAMES utf8mb4;

CREATE TABLE COMPANION_RELEASE (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     VARCHAR(20)   NOT NULL,           -- 예: 0.2.0
    notes       VARCHAR(500)  NULL,               -- 바뀐 점 (캐릭터 업데이트 말풍선에 나온다)
    file_name   VARCHAR(100)  NOT NULL,
    file_size   BIGINT        NOT NULL,
    sha256      CHAR(64)      NOT NULL,           -- 캐릭터가 내려받은 파일을 이 값으로 검사한다
    uploaded_by BIGINT        NULL,               -- 올린 관리자
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_release_version (version),
    KEY idx_companion_release_uploaded_by (uploaded_by),
    CONSTRAINT fk_companion_release_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES USERS (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE COMPANION_RELEASE_CHUNK (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    release_id  BIGINT        NOT NULL,
    seq         INT           NOT NULL,           -- 0부터 순서대로 이어 붙인다
    data        MEDIUMBLOB    NULL,               -- 최대 8MB. 오래된 버전은 NULL로 비운다
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_release_chunk_seq (release_id, seq),
    CONSTRAINT fk_companion_release_chunk_release
        FOREIGN KEY (release_id) REFERENCES COMPANION_RELEASE (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
