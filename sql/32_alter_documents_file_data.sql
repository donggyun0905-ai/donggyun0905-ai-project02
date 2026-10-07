-- 스펙 오디세이 (Spec Odyssey) — 서류 파일 내용을 DB에 저장 (2026-10-07)
-- 대상: 31번까지 실행한 DB. 한 번만 실행한다.
--       (03_schema_extended.sql에도 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "DOCUMENTS"
--
-- 서류 보관함·이력서·자소서·로드맵 증빙·프로젝트 제출 서류·연습장 노트는 지금까지 업로드한 PC의 폴더(UPLOAD_DIR)에
-- 파일을 쓰고 DOCUMENTS.file_path에 경로만 남겼다. DB는 팀이 같이 쓰는데 폴더는 PC마다 따로라, 다른 PC의 서버에서는
-- 그 서류가 404였다. 이제 파일 내용을 file_data에 넣는다(스펙 아카이브 사진을 DB로 옮긴 sql/20과 같은 이유).
--
--   file_data : 파일 내용. 서류 한 개 최대 20MB라 MEDIUMBLOB(16MB)이 아니라 LONGBLOB.
--               MySQL max_allowed_packet이 20MB보다 커야 업로드된다(공유 DB는 31MB).
--   file_path : 예전(디스크) 서류만 값이 있다 — 새 서류는 NULL이라 NOT NULL을 푼다.
--
-- 예전 서류는 서버를 켤 때 DocumentBlobBackfill이 "이 PC 디스크에 있는 것"만 골라 file_data로 옮긴다.
-- 팀원이 각자 한 번씩 서버를 켜면 각자 올렸던 서류가 채워진다. 옮기기 전까지는 지금처럼 디스크에서 읽는다.

ALTER TABLE DOCUMENTS
    MODIFY COLUMN file_path VARCHAR(500) NULL COMMENT '예전(디스크) 서류만 — 새 서류는 NULL',
    ADD COLUMN file_data LONGBLOB NULL COMMENT '파일 내용 (2026-10-07부터)' AFTER checksum;
