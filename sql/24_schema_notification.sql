-- 스펙 오디세이 (Spec Odyssey) — 알림: NOTIFICATION
-- 대상: 모든 DB(새 DB·운영 중인 DB). 한 번만 실행한다.
-- 기준 문서: docs/db-design.md "NOTIFICATION"
-- 헤더의 알림 버튼(빨간 표시·안 읽은 개수)과 알림 목록이 읽는 곳. 받는 사람(user_id) 기준으로 한 줄씩 쌓는다.
-- 알림 종류(noti_type):
--   COMMENT    내 스펙 아카이브 글에 댓글이 달림      ref_key = comment:<댓글 id>
--   REPLY      내 댓글에 답글이 달림                  ref_key = comment:<답글 id>
--   SHARE_VIEW 면접관이 내 공유 링크를 열람함(열 때마다) ref_key = view:<SHARE_LINK_VIEW_LOG id>
--   DDAY       D-day 하루 전·당일 (매일 09:00)        ref_key = dday:<DDAY_ALERT id>:<목표일>(하루 전) / dday-today:<DDAY_ALERT id>:<목표일>(당일)
--   MISSION    오늘의 미션을 다 안 풀었으면 23:00      ref_key = mission:<날짜>
-- 복합 UNIQUE(user_id, noti_type, ref_key): 스케줄러가 서버 재기동으로 두 번 돌아도 같은 알림이 두 번 쌓이지 않는다.

CREATE TABLE NOTIFICATION (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL, -- 받는 사람
    noti_type   VARCHAR(30)  NOT NULL, -- COMMENT / REPLY / SHARE_VIEW / DDAY / MISSION
    message     VARCHAR(200) NOT NULL,
    link_url    VARCHAR(300) NULL,     -- 누르면 이동할 앱 안 경로(컨텍스트 경로 제외, '/'로 시작)
    ref_key     VARCHAR(100) NOT NULL, -- 중복 방지 키 (위 설명)
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_user_type_ref (user_id, noti_type, ref_key),
    KEY idx_notification_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES USERS (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
