-- 스펙 오디세이 (Spec Odyssey) — 관리자 감사 로그: ADMIN_AUDIT_LOG
-- 대상: 모든 DB(새 DB·운영 중인 DB). 한 번만 실행한다.
-- 기준 문서: docs/db-design.md "ADMIN_AUDIT_LOG"
--
-- 관리자 화면(/admin/*)에서 남의 데이터를 바꾸는 행동을 남긴다. 2026-10-06에 관리자 기능
-- (게시판 내리기·회원 프로필 수정·비밀번호 재설정·탈퇴 처리·로드맵 단계 수정·기준 데이터 수정)을
-- 만들면서 "누가 언제 무엇을 했는지" 기록이 전혀 없었다 — 내려간 글을 누가 내렸는지 추적이 안 됐다.
--
-- action      : 무엇을 했는지 (예: ARTICLE_HIDE, USER_PASSWORD_RESET, ROADMAP_STEP_COMPLETE)
-- target_type : 대상 종류 (예: TECH_ARTICLE, USERS, ROADMAP_STEP, SKILL)
-- target_id   : 대상 행의 id (대상이 행 하나가 아니면 NULL)
-- detail      : 사람이 읽을 요약 한 줄 (예: "사유: 광고성 글")
--
-- admin_user_id는 ON DELETE SET NULL이다 — 다른 FK는 RESTRICT지만, 감사 기록이 남아 있다는
-- 이유로 관리자 계정을 못 지우게 되면 안 된다. 계정이 지워져도 기록 자체는 남아야 한다.
-- 기록은 고치지 않는다(append-only, SCORE_LOG와 같은 취지) — 그래서 수정 경로를 만들지 않는다.

SET NAMES utf8mb4;

CREATE TABLE ADMIN_AUDIT_LOG (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    admin_user_id  BIGINT       NULL,                -- 한 사람 (계정이 지워지면 NULL)
    admin_login_id VARCHAR(50)  NOT NULL,            -- 계정이 지워져도 누구였는지 남기려고 함께 적는다
    action         VARCHAR(40)  NOT NULL,
    target_type    VARCHAR(30)  NOT NULL,
    target_id      BIGINT       NULL,
    detail         VARCHAR(500) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_admin_audit_log_created (created_at),
    KEY idx_admin_audit_log_action (action, created_at),
    KEY idx_admin_audit_log_target (target_type, target_id),
    CONSTRAINT fk_admin_audit_log_admin
        FOREIGN KEY (admin_user_id) REFERENCES USERS (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
