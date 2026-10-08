-- 스펙 오디세이 (Spec Odyssey) — 스펙 아카이브 글 검색용 FULLTEXT 인덱스 (2026-10-08)
-- 대상: 36번까지 실행한 DB. 한 번만 실행한다.
--       (03_schema_extended.sql에도 반영되어 있어, 새로 만드는 DB에서는 실행할 필요가 없다.)
-- 기준 문서: docs/db-design.md "TECH_ARTICLE"
--
-- 스펙 아카이브에는 검색이 없었다. 제목·본문을 LIKE '%키워드%'로 찾으면 인덱스를 전혀 타지 못하고
-- (앞에 와일드카드가 있으면 B-tree를 쓸 수 없다) 본문은 TEXT라 글이 쌓일수록 느려진다.
--
-- ngram 파서를 쓴다. 기본 FULLTEXT 파서는 공백으로 단어를 자르기 때문에 한국어에서 "스프링 부트"를
-- 한 덩이로만 보고 "프링"으로는 못 찾는다. ngram은 글자 2개씩 쪼개 넣어 부분 일치가 된다.
-- 공유 DB 확인(2026-10-08): MySQL 9.7.2 · ngram 플러그인 ACTIVE · ngram_token_size = 2.
--
-- 한계: ngram_token_size가 2라서 한 글자 검색("자")은 걸리지 않는다. 그때는 코드가 LIKE로 떨어진다
-- (TechArticleDao.search). 토큰 크기를 1로 낮추면 인덱스가 급격히 커지고 흔한 글자가 전부 걸려 느려진다.

SET NAMES utf8mb4;

ALTER TABLE TECH_ARTICLE
    ADD FULLTEXT KEY ft_tech_article_text (title, content) WITH PARSER ngram;
