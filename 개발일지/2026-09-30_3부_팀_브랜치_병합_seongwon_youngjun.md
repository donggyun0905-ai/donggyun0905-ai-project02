# 팀 브랜치 병합 — seongwon(일일 미션) · youngjun(로컬 임베딩)

날짜: 2026-09-30
관련 요구사항: FR-51·52·53(일일 미션), TD-1(임베딩)

## 1. 개요

다른 담당자가 완성했다고 알려준 기능 두 개를 내 브랜치로 가져왔다. 둘 다 브랜치 전체를 병합하지 않고, 신규 파일만 선택적으로 `git checkout <branch> -- <path>`로 가져온 뒤 겹치는 공유 파일(스키마, db-design.md, `.env.example`)은 내 쪽 변경사항을 유지한 채 수기로 합쳤다 — 두 브랜치 모두 개발일지·API문서 PDF 등 이번 작업과 무관한 파일이 수십~백 개 단위로 같이 들어있어서, 통째로 머지하면 그런 것까지 끌려온다.

## 2. seongwon 브랜치 — 일일 미션 문제 기능

- `MissionProblemFilter`(`@WebFilter "/mission"`) — 기존 `MissionServlet`(화면 담당자 파일)은 안 건드리고 필터로 오늘의 추천 문제를 실어줌.
- `MissionSubmitServlet`/`MissionFailServlet`, `DailyMissionService`, `MissionSubmitService`, `CodeCompileService`, `Judge0Client`, `MissionDao` 가져옴 — "정답 입력하기"는 Judge0 API로 컴파일(문법)만 확인 후 완료 처리, "실패"는 완료+오답으로 표시.
- 스키마: `PROBLEM.category`(SQL/ALGORITHM, 목표 직무별 출제 비율 기준), `USER_DAILY_MISSION.submitted_code`/`submitted_language`/`submitted_at` 추가.
- `mission.jsp`를 고정 예시 데이터에서 실제 DB 표시로, `mission-submit.jsp` 신설.
- **병합 중 발견**: seongwon의 `.env.example`이 실수로 `GROQ_API_KEY` 섹션을 통째로 지워놓은 오래된 버전이었음 — 내 쪽 내용은 살리고 Judge0 섹션만 추가하는 식으로 합침.

## 3. youngjun 브랜치 — 로컬 임베딩(LocalEmbedder) 기초

- DJL + ONNX Runtime으로 ko-sroberta-multitask(768차원)를 로컬 실행하는 `LocalEmbedder` 가져옴. 모델 파일(약 440MB)은 저장소에 커밋하지 않고 `EMBEDDING_MODEL_DIR` 환경변수로 경로만 읽는다.
- `pom.xml`: DJL 0.38.0 BOM + api/onnxruntime-engine/tokenizers 의존성 추가, `src/main/resources`를 빌드 리소스로 명시.
- `docs/role-plan.md` 신설 — SkillMatcher 계약(실패 시 null 대신 `MatchResult.none()`) 문서. 이번에 처음 받아옴.
- 이 시점에는 모델 파일이 없어서 `LocalEmbedderTest`는 전부 건너뛰는 상태(`@BeforeAll assumeTrue`) — 실제로 모델을 받아 돌려본 건 이어지는 2부(시맨틱 매칭) 작업에서다.

## 4. 관련 커밋

```
9c8f98a feat: seongwon 브랜치 일일 미션 문제 기능 병합 (FR-51·52·53)
77f76e6 feat: youngjun 브랜치 로컬 임베딩(LocalEmbedder) 가져옴 (TD-1)
af5576b chore: 화면 설계 산출물을 PDF 병합본 하나로 정리
```
