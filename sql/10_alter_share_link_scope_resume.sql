-- 스펙 오디세이 (Spec Odyssey) — SHARE_LINK에 이력서 파일 공개 범위 추가
-- 대상: sql/03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03_schema_extended.sql에는 이 컬럼이 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "SHARE_LINK"
--
-- 지원자가 링크를 만들 때 "이력서 파일"을 체크한 링크로만 면접관이 이력서를 내려받을 수 있다.
-- 이력서에는 연락처 같은 개인정보가 들어 있어 기본값은 비공개(FALSE)다 — 이 컬럼이 생기기 전에
-- 만든 링크도 모두 비공개로 남는다(NFR-4 본인 선택 공유).

ALTER TABLE SHARE_LINK
    ADD COLUMN scope_resume BOOLEAN NOT NULL DEFAULT FALSE AFTER scope_growth;
