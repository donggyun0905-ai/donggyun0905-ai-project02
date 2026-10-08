-- 데스크톱 캐릭터(가이드) 연결 — COMPANION_DEVICE
-- 대상: 32번까지 실행한 DB. 한 번만 실행한다. (03_schema_extended.sql에도 반영되어 있어 새 DB에서는 필요 없다)
--       (donghyeon 브랜치에서는 32번이었다 — 같은 날 seongwon의 32번(서류 DB 저장)이 먼저 main에 들어가
--        33번으로 옮겼다. 내용은 그대로다.)
-- 기준 문서: docs/desktop-companion-plan.md 3·6절
--
-- 웹에서 "캐릭터 켜기" → 일회용 코드(1분)를 만들어 specodyssey:// 주소로 exe에 넘기고,
-- exe가 그 코드를 캐릭터 전용 토큰으로 바꾼다. 코드·토큰은 원문을 저장하지 않고 SHA-256 해시(64자)만 둔다.
-- 한 행 = 한 PC 연결. revoked_at이 있으면 연결 해제된 것 (토큰으로 더 이상 요청할 수 없다).

SET NAMES utf8mb4;

CREATE TABLE COMPANION_DEVICE (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    device_name        VARCHAR(100)  NULL,     -- PC 이름 (연결할 때 exe가 알려 줌)
    connect_code_hash  CHAR(64)      NULL,     -- 일회용 코드 해시 — 토큰으로 바꾸면 NULL
    code_expires_at    DATETIME      NULL,     -- 일회용 코드 만료 (발급 1분 뒤)
    token_hash         CHAR(64)      NULL,     -- 캐릭터 전용 토큰 해시 — 연결 전에는 NULL
    connected_at       DATETIME      NULL,
    last_used_at       DATETIME      NULL,
    revoked_at         DATETIME      NULL,     -- 연결 해제 시각
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_companion_device_token (token_hash),
    UNIQUE KEY uk_companion_device_code (connect_code_hash),
    KEY idx_companion_device_user (user_id),
    CONSTRAINT fk_companion_device_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
