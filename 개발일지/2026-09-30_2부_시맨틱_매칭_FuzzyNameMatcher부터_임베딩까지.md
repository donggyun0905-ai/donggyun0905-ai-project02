# 시맨틱 매칭 — FuzzyNameMatcher · SKILL_ALIAS · EmbeddingMatcher 완성

날짜: 2026-09-30
관련 요구사항: TD-1 임베딩 시맨틱 매칭

## 1. 개요

TD-1(임베딩 기반 시맨틱 매칭)이 한 달 가까이 "아직 못 함"으로 남아있던 걸, 담당자가 바뀌는 과정(youngjun → 나)을 거치며 하루 안에 3단계(편집거리 → 별칭 사전 → 실제 임베딩)로 끝까지 완성했다.

## 2. 1단계 — FuzzyNameMatcher (편집거리 기반 중간 단계)

- "시맨틱 매칭, 스킬 이름 일치라도 먼저 해야 한다"는 팀 요청으로 신설.
- 정확 일치(`SKILL.skill_name`) → 실패하면 SKILL 전체와 레벤슈타인 편집거리 비교, 이름 길이의 40%를 넘는 차이는 다른 기술로 보고 불인정(`ProfileService`의 직무명 퍼지 매칭과 같은 임계값 재사용).
- 공용 `EditDistanceUtil` 유틸로 분리해서 `ProfileService`도 자기 구현을 걷어내고 이걸 쓰도록 정리.
- `GapAnalysisService`·`JobDiscoveryService` 기본 매처를 `ExactMatcher` → `FuzzyNameMatcher`로 교체.

## 3. 2단계 — SKILL_ALIAS 사전 신설

- `FuzzyNameMatcher`는 편집거리만 봐서 "파이썬"↔"Python"처럼 표기 체계 자체가 다른 흔한 동의어는 여전히 못 잡는다는 한계가 바로 드러남.
- `JOB_ALIAS`와 같은 패턴으로 `SKILL_ALIAS` 테이블 신설. 1차로 163개 SKILL 중 142개에 흔히 쓰는 한글 표기·줄임말 시드(예: 파이썬→Python, 쿠버네티스→Kubernetes, JS→JavaScript). 애매하거나 이미 짧은 약어뿐인 21개(SQL, PHP, R, DNS, VPN 등)는 억지로 채우지 않고 비워둠.
- "영어도 올바르게 추출되게" 요청으로 2차 보강 — 영어권에서도 벤더/프로젝트 접두사를 빼고 부르는 표현 24개 추가(Postgres, Spark, Kafka, Azure, RoR 등). 현재 총 187건.
- `FuzzyNameMatcher` 매칭 순서를 "정확 일치(SKILL) → 정확 일치(SKILL_ALIAS) → 편집거리(SKILL+SKILL_ALIAS 통합 후보군)"로 재구성.
- 검증: 한글·영어 별칭 34개 예시를 `@ParameterizedTest`로 한 번에 확인하는 테스트 추가.

## 4. 3단계 — EmbeddingMatcher 완성 (진짜 TD-1)

youngjun이 미리 만들어둔 `LocalEmbedder`(DJL+ONNX, ko-sroberta-multitask)를 이어받아 담당자 논의 끝에 직접 마무리하기로 함.

- 모델 파일(model.onnx + tokenizer.json, 약 420MB)을 `huggingface.co/jhgan/ko-sroberta-multitask`에서 직접 받아 `EMBEDDING_MODEL_DIR`에 설정 — `LocalEmbedderTest` 5개가 실제 모델로 전부 통과하는 것부터 확인.
- `EmbeddingMatcher` 신설: `FuzzyNameMatcher`(정확 일치·SKILL_ALIAS·편집거리)에 위임하고, 그래도 실패했을 때만 로컬 임베딩 코사인 유사도로 넘어간다. 모델 로딩이 수 초 걸려서 프로세스당 한 번만 로드해 재사용(static, lazy). 모델 파일이 없는 PC에서는 첫 시도에서 조용히 포기하고 그 뒤로는 `FuzzyNameMatcher` 결과만 쓰도록 해서 팀원 전원이 440MB를 받아둘 필요는 없게 함.
- `EmbeddingBackfillService` 신설 — `SKILL.embedding_vector`를 채우는 배치. 직접 돌려서 SKILL 181건(시드 163 + 테스트로 늘어난 행 포함) 전부 임베딩 완료, 팀 공유 DB 반영.
- `GapAnalysisService`·`JobDiscoveryService` 기본 매처를 `EmbeddingMatcher`로 최종 교체.

### 임계값 실측 — 0.85가 아니라 0.75로 결정한 이유

처음엔 임계값을 0.85로 잡았는데 "편집거리로는 못 잡지만 의미가 비슷한 문장" 테스트가 실패해서 직접 코사인 유사도를 측정해봤다.

| 비교 | 유사도 | 비고 |
| --- | --- | --- |
| Java ↔ JavaScript (다른 기술) | 0.805 | 오탐 위험 |
| 자바 ↔ Java (같은 기술, 표기만 다름) | 0.680 | 오히려 더 낮음 |
| "웹 서버 구축 기술" ↔ "백엔드 서버 개발 능력" (진짜 비슷한 문장) | 0.783 | 진짜 유사 |

진짜 유사 문장(0.783)이 오탐 위험 쌍(0.805)보다 점수가 낮아서, 어떤 임계값을 잡아도 짧은 기술명끼리는 완벽히 못 가른다는 결론. 그래서 임계값(0.75)은 진짜 유사 문장을 놓치지 않는 쪽으로 잡았고, 짧은 이름끼리의 오탐은 실무에서는 대부분 SKILL_ALIAS 사전이 먼저 정확 일치로 걸러준다는 전제로 감수하기로 함(사전에 없는 새 조합에서는 여전히 오탐 가능성 있음 — db-design.md에 근거 기록).

## 5. 관련 커밋

```
4292ef5 feat: 시맨틱 매칭 중간 단계 — FuzzyNameMatcher (이름 일치라도)
7705933 feat: SKILL_ALIAS 사전 신설 — 한글 표기·줄임말 163개 시딩
3dcfa1c feat: SKILL_ALIAS 영어 표기 보강 + 예시 기반 검증 테스트
64a72d0 feat: TD-1 임베딩 매칭 완성 — EmbeddingMatcher + SKILL 181건 백필
```
