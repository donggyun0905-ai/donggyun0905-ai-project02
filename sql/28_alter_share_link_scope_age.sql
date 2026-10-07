-- 스펙 오디세이 (Spec Odyssey) — SHARE_LINK에 나이 공개 범위 추가
-- 대상: sql/03_schema_extended.sql로 이미 테이블을 만든 DB에만 실행한다.
--       (03_schema_extended.sql에는 이 컬럼이 이미 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "SHARE_LINK"
--
-- 면접관의 "지원자 비교 > 나란히 보기"에서 나이 표시·나이순 정렬에 쓴다.
-- 나이는 지원자가 링크를 만들 때 "나이"를 체크한 링크로만 보인다. 기본값은 비공개(FALSE)라
-- 이 컬럼이 생기기 전에 만든 링크도 모두 비공개로 남는다(NFR-4 본인 선택 공유).

ALTER TABLE SHARE_LINK
    ADD COLUMN scope_age BOOLEAN NOT NULL DEFAULT FALSE AFTER scope_cover_letter;
