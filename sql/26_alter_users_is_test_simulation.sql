-- 테스트 계정 표시 + 70일 시뮬레이션 진행 상태
-- 대상: 23번까지 실행한 DB. 한 번만 실행한다. (03_schema_extended.sql·01_schema.sql에도 반영되어 있어 새 DB에서는 필요 없다)
--
-- USERS.is_test   : 테스트 계정이면 TRUE. 오른쪽 위 시뮬레이션 / 일시정지 / 초기화 버튼은 이 계정에만 보이고,
--                   서버도 요청마다 이 값을 다시 확인한다. 초기화는 테스트 계정에 한해 예외로 물리 삭제한다.
-- SIMULATION_STATE: 계정별 시뮬레이션 진행 상태 (몇 일째인지 · 실행/일시정지/완료). 서버를 다시 켜도 이어서 할 수 있게 DB에 둔다.

SET NAMES utf8mb4;

ALTER TABLE USERS
    ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT FALSE COMMENT '테스트 계정 — 시뮬레이션 버튼 표시, 초기화 시 물리 삭제 허용' AFTER user_type;

CREATE TABLE SIMULATION_STATE (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    status       VARCHAR(10)   NOT NULL,           -- RUNNING / PAUSED / DONE
    persona      VARCHAR(20)   NOT NULL,           -- DILIGENT(성실) / STEADY(보통) / ON_OFF(작심삼일)
    start_date   DATE          NOT NULL,           -- 시뮬레이션 첫날 (시작한 날 기준 70일 전)
    total_days   INT           NOT NULL,           -- 70
    days_done    INT           NOT NULL DEFAULT 0, -- 끝낸 날 수 — 일시정지 후 이 다음 날부터 이어 간다
    started_at   DATETIME      NOT NULL,           -- 처음 시작한 실제 시각 — 초기화 때 이후에 생긴 프로필 행을 지우는 기준
    last_error   VARCHAR(500)  NULL,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_simulation_state_user (user_id),
    CONSTRAINT fk_simulation_state_user FOREIGN KEY (user_id) REFERENCES USERS (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지금 DB에 있는 테스트용 계정을 테스트 계정으로 표시한다 (admin은 관리자 계정이라 제외).
-- LIKE의 _는 아무 글자나 뜻하므로 \_로 막는다.
UPDATE USERS SET is_test = TRUE
WHERE login_id <> 'admin'
  AND (login_id LIKE 'roadmap\_test\_%'
    OR login_id LIKE 'gap\_test\_%'
    OR login_id LIKE 'spec\_score\_test\_%'
    OR login_id LIKE 'test\_reset\_%'
    OR login_id LIKE 'insight\_%'
    OR login_id LIKE 'del\_%'
    OR login_id LIKE 'smoke%'
    OR login_id IN ('test', 'test1', 'test1234', 'webtest', '개척자', '오디세이아', 'aaa', 'bbb', 'ttt'));
