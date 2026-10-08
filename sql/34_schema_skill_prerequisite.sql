-- 스펙 오디세이 (Spec Odyssey) — 기술 선수관계 SKILL_PREREQUISITE (2026-10-08)
-- 대상: 33번까지 실행한 DB. 한 번만 실행한다.
--       (03_schema_extended.sql에도 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "SKILL_PREREQUISITE"
--
-- 로드맵 순서는 지금까지 "필수냐 우대냐"와 티어로만 정했다. 그래서 Spring이 Java보다 먼저 나오거나
-- Docker가 Linux보다 먼저 나오는 순서가 만들어질 수 있었다. 이 테이블이 "A를 하기 전에 B" 관계를 담고,
-- 로드맵을 만들 때 위상 정렬(Kahn)로 순서를 정한다.
--
--   skill_id        : 뒤에 와야 하는 기술 (예: Spring)
--   prereq_skill_id : 먼저 와야 하는 기술 (예: Java)
--
-- 관계는 방향이 있는 그래프이고 순환(A→B→A)이 생기면 위상 정렬이 불가능하다. 그래서 추가할 때
-- DFS로 순환을 막는다(SkillPrerequisiteService.wouldCreateCycle) — 자기 자신(A→A)도 그 검사가
-- 잡는 가장 짧은 순환이다. CHECK(skill_id <> prereq_skill_id)로도 막고 싶었지만, MySQL은
-- 참조 동작(ON UPDATE CASCADE)이 걸린 컬럼을 CHECK에 쓸 수 없다(ERROR 3823).

SET NAMES utf8mb4;

CREATE TABLE SKILL_PREREQUISITE (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    skill_id        BIGINT   NOT NULL COMMENT '뒤에 와야 하는 기술',
    prereq_skill_id BIGINT   NOT NULL COMMENT '먼저 와야 하는 기술',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    -- 같은 관계를 두 번 넣으면 위상 정렬의 진입차수가 어긋난다
    UNIQUE KEY uk_skill_prerequisite (skill_id, prereq_skill_id),
    KEY idx_skill_prerequisite_prereq (prereq_skill_id),
    CONSTRAINT fk_skill_prerequisite_skill
        FOREIGN KEY (skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_skill_prerequisite_prereq
        FOREIGN KEY (prereq_skill_id) REFERENCES SKILL (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
