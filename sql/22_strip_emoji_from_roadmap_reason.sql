-- 스펙 오디세이 (Spec Odyssey) — 로드맵 단계 설명(ROADMAP_STEP.reason) 앞의 이모지 제거
-- 대상: 이미 운영 중인 DB. 화면의 이모지를 선 아이콘으로 바꾸면서(2026-10-03) 코드가 만드는 설명에서도 이모지를 뺐다.
-- 이 파일은 그 전에 저장된 설명을 새 형식으로 맞춘다. 여러 번 실행해도 안전하다(이미 바꾼 행은 조건에 걸리지 않는다).
--
-- 주의: utf8mb4_unicode_ci에서는 서로 다른 이모지를 같은 글자로 볼 수 있어서, 비교는 반드시 COLLATE utf8mb4_bin으로 한다.
-- LEFT/SUBSTRING은 글자 수 기준이라 "이모지 + 공백"은 2글자다.

SET NAMES utf8mb4;

-- AI 프로젝트 아이디어: "💡 제목 — 설명" → "아이디어: 제목 — 설명" (roadmap.jsp가 이 앞머리로 제목만 골라 보여준다)
UPDATE ROADMAP_STEP SET reason = CONCAT('아이디어: ', SUBSTRING(reason, 3))
WHERE LEFT(reason, 2) COLLATE utf8mb4_bin = '💡 ';

-- 트렌딩 학습: "📈 트렌딩 학습 — ..." → "트렌딩 학습 — ..." (RoadmapUpkeepService.TREND_REASON_PREFIX와 같아야 이미 공부한 기술을 알아본다)
UPDATE ROADMAP_STEP SET reason = SUBSTRING(reason, 3)
WHERE LEFT(reason, 2) COLLATE utf8mb4_bin = '📈 ';

-- 복습·프로젝트 업데이트·기술 글 업데이트: 앞의 이모지만 뗀다 (아이콘은 화면이 단계 종류로 그린다)
UPDATE ROADMAP_STEP SET reason = SUBSTRING(reason, 3)
WHERE LEFT(reason, 2) COLLATE utf8mb4_bin IN ('🔁 ', '🛠 ', '📝 ');
