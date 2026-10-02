-- 스펙 아카이브 — 업로드 사진을 DB에 저장한다
-- 대상: 19_alter_tech_article_spec_archive.sql까지 실행한 DB. 한 번만 실행한다.
--       (03_schema_extended.sql에도 같은 내용이 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
--
-- 이유: 사진 파일을 서버 PC 폴더(UPLOAD_DIR)에 두면, 팀원마다 자기 PC에서 서버를 켜고 같은 DB를 쓰는 구조에서
--       다른 사람이 올린 사진이 안 보인다. 사진 내용(바이트)을 DB에 넣어 어느 서버에서든 보이게 한다.
-- MEDIUMBLOB = 최대 16MB. 글 하나의 사진 합계가 10MB라(SpecArchiveRules) 한 장도 이 안에 들어간다.
-- 목록·상세 조회는 이 컬럼을 읽지 않고, 이미지 주소(/spec-archive/image/{id})로 요청할 때만 읽는다.
-- file_path · stored_name은 예전 디스크 저장 행을 위해 남겨 둔다 (새 행은 비어 있다).

SET NAMES utf8mb4;

ALTER TABLE TECH_ARTICLE_ATTACHMENT
    ADD COLUMN file_data MEDIUMBLOB NULL COMMENT 'IMAGE_UPLOAD 사진 내용 — 어느 서버에서든 보이게 DB에 저장' AFTER mime_type;
