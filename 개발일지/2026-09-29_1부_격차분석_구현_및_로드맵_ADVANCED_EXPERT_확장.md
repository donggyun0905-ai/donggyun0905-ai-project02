# 격차 분석 실제 구현 및 로드맵 ADVANCED/EXPERT 티어 확장

날짜: 2026-09-29
범위: FR-31(격차 분석 규칙기반 대조), FR-32·33·36(로드맵 생성·이유·완료체크→진행도) — 로드맵 담당(B) 영역

## 1. 개요

이전까지 `RoadmapService.generate()`는 이미 완성돼 있었지만, 그 입력이 되는 `GapAnalysisService`가 아직 구현되지 않아 실제로는 동작을 검증할 방법이 없었다. 또한 `RoadmapService`가 만드는 로드맵은 `ROADMAP_STEP.tier`가 스키마상 `ENTRY/CORE/ADVANCED/EXPERT` 4단계를 지원하는데도 실제로는 ENTRY/CORE 2단계만 생성하고 있었다(`docs/db-design.md`가 명시한 "끝없는 여정" 설계와 어긋남). 오늘 이 두 가지를 이어서 완성했다.

## 2. GapAnalysisService 구현

`JOB_REQUIRED_SKILL`(직무별 요구 기술)과 사용자 보유 스킬(`USER_SKILLS`)을 대조해 `GAP_ANALYSIS`/`GAP_ANALYSIS_ITEM`을 생성한다.

- 매칭 방식: `skill_id`가 이미 연결된 경우 그대로 비교, 아니면 `raw_input`을 표준 `SKILL.skill_name`과 대소문자만 무시하고 정확 일치 비교(시멘틱/임베딩 매칭은 TD-1 범위로 아직 보류 — 팀에서 별도 확인 필요).
- `matchRate`는 `metCount / totalCount * 100`(HALF_UP, scale 2), 요구 기술이 0건이면 0으로 처리.
- 한 트랜잭션으로 `GAP_ANALYSIS` 1건 + `GAP_ANALYSIS_ITEM` N건(MET/MISSING) 저장.

### 버그: `GapAnalysisDao.findByUserId`의 불안정한 정렬

- **문제**: `ORDER BY analyzed_at DESC`만 있었는데, `analyzed_at`이 `DATETIME`이라 초 단위 정밀도다. 같은 초에 분석을 두 번 하면(테스트에서 실제로 재현됨: `expected: <63> but was: <62>`) "가장 최근 분석"의 순서가 보장되지 않는다.
- **왜 문제인가**: `RoadmapService.generate()`가 `analyses.get(0)`을 "가장 최근"으로 가정하는데, 순서가 흔들리면 엉뚱한(오래된) 분석 결과로 로드맵을 만들 수 있다.
- **어떻게 고쳤나**: `ORDER BY analyzed_at DESC, id DESC`로 2차 정렬 키를 추가. `AUTO_INCREMENT` PK는 삽입 순서를 항상 보장하므로 동시각 문제를 근본적으로 없앤다. (테스트에 `Thread.sleep`을 넣어 우회하는 대신 DAO 자체를 고쳤다.)

## 3. RoadmapService — ADVANCED/EXPERT 확장

- `SKILL_TIER_ORDER = [ENTRY, CORE, ADVANCED, EXPERT]`로 확장. 부족 기술(점수 내림차순)을 `chunkForTier()`로 티어당 5개씩(`MAX_SKILL_STEPS_PER_TIER`) 자르고, 마지막 티어(EXPERT)만 남은 걸 전부 받는다 — 기술이 아무리 많아도 버려지지 않는다.
- CERT/PROJECT 단계는 여전히 ENTRY 티어에만 생성(여정의 첫 진입점 성격).
- `computeProgress()`를 완전히 재설계: `RoadmapProgress`가 티어별 `TierProgress`(총 개수·완료 개수·퍼센트·언락 여부) 리스트를 갖는 구조로 바꿨다. 계단식 언락 로직 — 앞 티어가 "언락 + (완료 또는 비어있음)"이어야 다음 티어가 언락된다. 매번 `steps` 원본에서 새로 계산하므로, 완료 취소로 앞 티어가 다시 미완료가 되면 뒤 티어도 그 즉시 재잠금된다.
- 화면(JSP)에서 티어 이름을 하드코딩하지 않도록 `getCurrentTier()`(지금 할 일로 보여줄 티어), `getNextLockedTier()`(다음 단계 미리보기), `isJourneyComplete()` 편의 메서드를 추가했다.

## 4. 테스트

- `GapAnalysisServiceTest`(3개): 스킬 매칭(정확 일치 + 대소문자 다른 케이스), MET/MISSING 판정, 그리고 **`analyze()` → `RoadmapService.generate()`가 실제로 이어져 동작하는지**를 증명하는 테스트를 추가했다 — 이게 되면 격차분석과 로드맵이 진짜로 연결됐다는 증거가 된다.
- `RoadmapServiceTest`: 기존 2티어 가정 테스트(`진행도는_ENTRY_티어_기준으로...`)를 새 API(`TierProgress`)로 고치고, 부족 기술 12개로 ENTRY(5)/CORE(5)/ADVANCED(2) 생성 + EXPERT는 비어있음 + ENTRY 완료 시 CORE가 풀리는 계단식 언락을 검증하는 테스트를 새로 추가했다.
- 전체 테스트(공유 원격 DB 기준) 통과 확인.

## 5. 관련 커밋

```
09345d9 feat: 격차 분석 실제 구현 및 로드맵 ADVANCED/EXPERT 티어 확장
```
