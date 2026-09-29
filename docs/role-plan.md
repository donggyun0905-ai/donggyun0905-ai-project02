# 2주차 역할 분담 조정안

> 범위: 격차 분석 + 로드맵 + 직무 발굴 + API 실패 대응 (FR-31·32·34·38·39, FR-111·112)
> 기간: 9/28(월) ~ 10/2(금) · 인원: donggyun0905, seongwon, youngjun, donghyeon, kangdain

---

## 1. 원안 검토

원안은 FR과 DAO 기준으로 영역을 깔끔하게 나눴고, 임베딩·LLM이 다른 영역의 선행 조건이라는 점도 정확히 짚었습니다. 브랜치 + PR 리뷰 + 작게 자주 병합하는 방식도 그대로 유지합니다.

다만 **담당자가 없는 일이 다섯 가지** 있습니다.

| 빠진 것 | 왜 문제인가 |
| --- | --- |
| **화면과 여정 흐름** | 프로젝트에 CSS 파일이 하나도 없습니다. 다섯 명이 각자 JSP를 만들면 화면이 다섯 가지 모양이 됩니다. 게다가 2주차 핵심 흐름인 "직무 발굴 → 격차 분석 → 로드맵"은 **한 사용자의 한 여정인데 세 사람의 화면에 걸쳐** 있습니다. FR-39("막다른 길 방지")가 바로 이 이음새입니다 |
| **기준 데이터** | `SKILL`과 `JOB_REQUIRED_SKILL`이 비어 있습니다. 이 둘이 없으면 격차 분석 결과는 빈 화면이고, 로드맵도 만들 재료가 없습니다. **2주차의 진짜 병목인데 담당자가 없습니다** |
| **⑤의 병목 해소 방법** | 임베딩(DJL 세팅 1~2일, 실패 위험 있음)과 LLM(벤더 미정)을 한 사람이 맡고, 나머지 네 명이 기다리는 구조입니다. ⑤가 늦으면 전원이 늦습니다 |
| **FR-111의 주인** | FR-111(AI API 실패 대응)을 ④에만 두었는데, LLM 실패는 로드맵(②)과 직무 발굴(③)에서 납니다. 실패 처리 방식을 한 곳에서 정하지 않으면 화면마다 다르게 처리됩니다 |
| **넘겨받을 항목** | `dao-guide.md` 5절에 적어둔 보류 항목(재수집 동기화, 마스터 저장 메서드 등)에 담당자가 없습니다 |

### 프론트 전담을 따로 두지 않는 이유

JSP 프로젝트에서 화면만 맡는 사람을 두면, 그 사람은 다른 네 명의 백엔드가 끝날 때까지 기다려야 합니다. 각 기능의 화면은 그 데이터를 가장 잘 아는 기능 담당자가 만드는 게 낫습니다.

대신 **공통 화면 기반(CSS, 공통 컴포넌트, 화면 사이 연결)**을 책임지는 사람이 필요합니다. 이 역할은 가장 가벼운 백엔드 영역과 묶습니다. 매칭 기능만 주어지면 SQL 대조로 끝나는 **격차 분석이 가장 가볍고**, 여정의 한가운데라 앞뒤 화면을 이어야 하는 위치이기도 합니다.

---

## 2. 조정안 — 역할 다섯 개

| 역할 | 한 줄 요약 | 이런 사람에게 | 담당자 |
| --- | --- | --- | --- |
| **A. 격차 분석 + 화면 흐름** | 내 스킬과 직무 요구 기술을 대조하고, 모든 화면의 공통 모양과 화면 사이 연결을 책임 | 화면 감각이 있고 SQL이 편한 사람 | 길동현 |
| **B. 로드맵** | 격차 분석 결과로 순서 있는 길을 만듦 | 규칙과 로직 설계를 좋아하는 사람 | 강동균 |
| **C. 직무 발굴** | 희망 직무가 없는 사용자에게 후보 직무를 찾아줌 | 기획 감각이 있는 사람 | 강다인 |
| **D. 데이터 파이프라인** | 기준 데이터를 채우고 워크넷과 캐시를 연동 | 꼼꼼하고 데이터 정리에 강한 사람 | 김성원 |
| **E. AI 기반** | 스킬 매칭(임베딩)과 LLM 호출을 모두가 쓸 수 있게 제공 | 새 기술 세팅이 빠른 사람 | 조영준 |

**PR 병합과 통합 책임자를 한 명** 따로 정해두면 좋습니다. 보통 레포 관리자가 맡되, 그 사람은 부담이 적은 역할과 겸하는 게 좋습니다.

### A. 격차 분석 + 화면 흐름

**백엔드** — FR-31·42

- `GapAnalysisService` 작성: `JobRequiredSkillDao.findByJobId`(요구)와 `UserSkillDao.findByUserId`(보유)를 E의 `SkillMatcher`로 대조
- `GapAnalysisDao` + `GapAnalysisItemDao`를 `TransactionUtil`로 묶어 저장
- 격차 분석 저장과 로드맵 생성을 **같은 트랜잭션**으로 처리 (B와 3절 계약 참고)

**화면 기반** — 전 화면 공통

- 공통 CSS 파일과 `header.jsp`·`footer.jsp` 연결 (**이 세 파일은 A만 수정**)
- 공통 요소: 표, 버튼, 로딩 표시(NFR-5), 빈 상태 안내(FR-114), 오류·재시도 안내(FR-111 화면 부분)
- 화면 흐름 규약 정의와 화면 사이 이동 연결 (3절)

3주차 대시보드와 여정 지도(FR-41~43)는 이 공통 기반 위에 올라가므로, 3주차에도 A가 중심이 되기 자연스럽습니다.

### B. 로드맵

FR-32 먼저, FR-33(단계별 이유)과 FR-36(완료 체크)은 여유가 있을 때 진행합니다.

- `RoadmapService` 작성: 격차 분석 결과의 부족 항목 → tier별 단계 → ①자격증 → ②프로젝트 → ③스킬 순서
- `CertificationDao`에 **직무별 조회 메서드 추가**. `job_category IN (:target, 'COMMON')` 조건 필수 (특이사항 ⑦)
- 기존 로드맵 비활성화: `RoadmapDao.updateActiveAndPrimary(conn, id, userId, false, false)`
- `JobBenchmarkSpecDao` 기준 데이터는 E의 LLM으로 생성해서 `sql/06_seed_benchmark.sql`로 작성
- **첫 산출물**: 로드맵 생성 규칙 문서. tier별 단계 수, 부족 항목이 많을 때 무엇을 먼저 넣을지, 현재 tier와 다음 tier만 노출하는 방식

FR-36 완료 체크에 **점수를 연결하는 시점**에는 `RoadmapStepDao.updateCompleted`의 반환 타입을 `int`로 바꿔야 합니다(특이사항 ①). 2주차에 점수까지 붙이지 않는다면 이번에는 해당되지 않습니다.

### C. 직무 발굴

FR-34·38·39

- `JobDiscoveryService` 작성: 설문 점수 + 보유 스펙·전공 역산 → LLM이 종합 → 후보 3~5개
- 설문 문항 작성: `sql/05_seed_survey.sql` (`survey_type = 'JOB_DISCOVERY'`)
- 후보 선택 → `JobRecommendationDao.updateSelected(conn, id, userId, true)` → 격차 분석 화면으로 이동 (FR-39)
- **정할 것**: 설문을 다시 풀 수 있게 할지. `USER_SURVEY_ANSWER`에 `(user_id, question_id)` UNIQUE가 있고 수정 메서드가 없습니다. 재응답을 허용하려면 메서드를 추가해야 합니다
- **첫 산출물**: 설문 문항과 점수 규칙 (어떤 응답이 어떤 직무 계열을 가리키는지)

### D. 데이터 파이프라인

**기준 데이터가 최우선입니다.** 명세서 TD-2도 "초기엔 AI 보조로 수기 구축 → 이후 워크넷"입니다. 워크넷을 기다리지 말고 먼저 채우세요.

- **9/28(월)**: 워크넷 인증키 신청 (발급에 시간이 걸릴 수 있음)
- **9/29(화)까지**: `sql/04_seed_skills.sql`
  - `SKILL` 100~200개 (IT 기술 표준 명칭)
  - `JOB_REQUIRED_SKILL` 18개 직무 × 직무당 10~15개
  - `JOB_ALIAS` 직무당 3~5개
- `JobAliasDao`에 insert 추가
- 워크넷 연동 + `ExternalApiCacheDao`로 캐시, FR-112 실패 대응 (캐시 대체 또는 "일시적으로 불러올 수 없음")
- **재수집 동기화 전략** 결정: 완료 기준은 **같은 배치를 연속 두 번 돌려도 성공** + 사라진 기술 처리 방식 (가이드 5절)
- `JobRequiredSkillDao`는 **D가 쓰고 A가 읽습니다.** 컬럼 의미를 바꾸면 A에게 알려주세요

워크넷 응답은 XML이라 `ExternalApiClient.parseJson`을 쓸 수 없습니다(특이사항 ⑤).

### E. AI 기반 ← **내 담당**

**원칙: 인터페이스를 9/28(월)에 먼저 내놓고, 실제 구현은 뒤에서 교체합니다.** 그래야 나머지 네 명이 기다리지 않습니다.

- **9/28(월)**: `SkillMatcher` 인터페이스 + `ExactMatcher`(이름 정확 일치) 병합
- **9/28(월)**: `LlmClient` 인터페이스 + `StubLlmClient`(고정 JSON 반환) 병합
- 이후: `EmbeddingMatcher`(DJL + ONNX) → 완성되면 `ExactMatcher`와 교체
- 이후: LLM 벤더가 정해지면 실제 `LlmClient` 구현. **타임아웃은 직접 지정** (특이사항 ④)
- `SkillDao`에 insert와 임베딩 갱신 메서드 추가
- **FR-111 공통 처리**: 상태 코드로 재시도 여부 판단(429·5xx·-1은 재시도, 400·401은 불가), 실패 시 화면에 넘길 결과 형식 정의. 화면 표시는 A와 합의

**가장 위험한 역할입니다.** DJL 세팅이 **9/30(수)까지 안 되면 임베딩 API 방식으로 전환**하세요. `SKILL.embedding_model` 컬럼이 이 전환을 위해 있습니다. `ExactMatcher`로도 여정은 끝까지 돌아가므로, 임베딩이 늦어져도 2주차 목표는 지킬 수 있습니다.

---

## 3. 1일차(9/28) 계약 — 기다림을 없애는 핵심

원안의 "⑤가 먼저 최소 기능을 만들어 공유"를 한 단계 더 당깁니다. **완성된 기능이 아니라 인터페이스만 먼저 합의하면, 구현을 기다리지 않고 모두 동시에 시작할 수 있습니다.**

### 스킬 매칭 (E 제공, A·C 사용)

```java
package com.specodyssey.service;

/** 사용자가 입력한 기술 원문을 표준 스킬(SKILL)에 연결한다. */
public interface SkillMatcher {
    /** 못 찾으면 null. score는 0~1 (정확 일치는 1.0) */
    MatchResult match(String rawInput) throws SQLException;

    record MatchResult(Long skillId, double score) {}
}
```

`ExactMatcher`는 `SkillDao.findByName`으로 대소문자·공백을 무시하고 찾아서 score 1.0을 줍니다. 첫날에 충분히 만들 수 있는 분량입니다.

### LLM 호출 (E 제공, B·C·D 사용)

```java
package com.specodyssey.util;

public interface LlmClient {
    /** 프롬프트를 보내고 응답 JSON을 type으로 파싱해 돌려준다 */
    <T> T completeJson(String prompt, Class<T> type) throws ExternalApiClient.ExternalApiException;
}
```

`StubLlmClient`는 요청과 상관없이 정해둔 예시 JSON을 돌려줍니다. B와 C는 벤더가 정해지기 전에도 이걸로 화면까지 개발할 수 있습니다.

### 격차 분석과 로드맵의 트랜잭션 경계 (A·B 합의)

격차 분석 저장과 로드맵 생성은 한 트랜잭션이어야 합니다. 분석만 저장되고 로드맵이 실패하면 사용자는 결과는 있는데 길이 없는 상태가 됩니다.

```java
// B가 제공 — 커넥션을 받아서 A의 트랜잭션 안에서 실행된다
public Long createRoadmap(Connection conn, Long userId, Long gapAnalysisId,
                          List<GapAnalysisItemDto> missingItems) throws SQLException

// A의 GapAnalysisService 안에서
TransactionUtil.runInTransaction(conn -> {
    Long analysisId = gapAnalysisDao.insert(conn, analysis);
    // ... 항목 저장
    roadmapService.createRoadmap(conn, userId, analysisId, missing);
    return analysisId;
});
```

### 화면 흐름 규약 (A가 정의)

| URL | 담당 | 화면 |
| --- | --- | --- |
| `/profile` | (1주차 완료) | 프로필 — 희망 직무가 있으면 "격차 분석 보기", 없으면 "직무 찾기"로 연결 |
| `/discover` | C | 설문 → 후보 직무 3~5개 → 선택 |
| `/analysis?jobId=…` | A | 격차 분석 실행과 결과 (충족·부족 비교표) |
| `/roadmap` | B | 메인 로드맵 (`is_primary`) |

**이동 순서**: 희망 직무가 없으면 `/discover`에서 후보를 선택하고 `/analysis?jobId=…`로 넘어갑니다. 여기서 분석과 로드맵을 한 트랜잭션으로 생성한 뒤 `/roadmap`으로 넘어갑니다. 희망 직무가 있으면 `/profile`에서 바로 `/analysis`로 갑니다.

새 URL은 `SessionFilter`가 자동으로 로그인을 요구하니 따로 설정할 필요가 없습니다.

---

## 4. 충돌 방지 규칙

서블릿 8개가 전부 `@WebServlet`으로 등록돼 있어 `web.xml`에서 충돌할 일은 없습니다. 충돌이 날 곳은 아래입니다.

| 파일 | 규칙 |
| --- | --- |
| `header.jsp`, `footer.jsp`, 공통 CSS | **A만 수정.** 메뉴 추가가 필요하면 A에게 요청 |
| `sql/02_seed.sql` | **수정 금지.** 시드는 담당자별 파일로 분리: `04_seed_skills.sql`(D), `05_seed_survey.sql`(C), `06_seed_benchmark.sql`(B). 실행 순서는 README에 추가 |
| `web.xml` | 수정 금지. 에러 페이지와 인코딩은 이미 설정됨 |
| DAO | 메서드 **추가는 자유.** 기존 메서드의 **시그니처 변경은 PR에 사용처를 적고** `dao-guide.md`도 같이 갱신 |
| `SessionFilter` 공개 경로 | 변경 시 **리뷰어 2명** (보안 경계라서) |
| `JobRequiredSkillDao` | 쓰기는 D, 읽기는 A |

---

## 5. 일정

| 날짜 | 전원 | A | B | C | D | E |
| --- | --- | --- | --- | --- | --- | --- |
| 9/28 월 | **LLM 벤더 결정**, 1일차 계약 합의 | 화면 흐름 규약, 공통 CSS 초안 | 로드맵 생성 규칙 설계 | 설문 문항·점수 규칙 설계 | 워크넷 키 신청, 시드 착수 | `SkillMatcher` + `ExactMatcher`, `LlmClient` + Stub 병합 |
| 9/29 화 |  | 격차 분석 서비스 착수 | 로드맵 서비스 착수 | 설문 시드 병합, 발굴 서비스 착수 | **기준 데이터 시드 1차 병합** | DJL 세팅 |
| 9/30 수 |  | 격차 분석 동작 (정확 일치 매칭) | 격차 분석 결과로 로드맵 생성 | 설문 → 후보 (Stub LLM) | 워크넷 연동 | **임베딩 판단 시점** — 안 되면 API로 전환 |
| 10/1 목 | **여정 연결 통합** | 발굴 → 분석 → 로드맵 화면 연결 | 벤치마크 시드 | 실제 LLM으로 교체 | 캐시 + FR-112 | 임베딩 매칭 교체, FR-111 공통 처리 |
| 10/2 금 | 실패 시나리오 점검, 2주차 개발일지 |  |  |  | 연속 2회 배치 테스트 |  |

**LLM 벤더는 9/28(월)에 반드시 정하세요.** B·C·D와 벤치마크 시드까지 전부 LLM에 걸려 있습니다. Stub으로 버틸 수 있는 건 10/1(목)까지입니다.

---

## 6. 넘겨받는 항목 배정

`dao-guide.md` 5절과 6절의 항목을 역할에 연결했습니다.

| 항목 | 역할 | 2주차 범위 |
| --- | --- | --- |
| 재수집 동기화 전략 (UPSERT 4건 포함) | D | ✅ |
| 캐시 헬퍼 추출 여부 | D (E의 LLM 캐시가 두 번째 사용처가 되면 함께 결정) | ✅ |
| `SkillDao` insert·임베딩 갱신 | E | ✅ |
| `JobAliasDao` insert | D | ✅ |
| `CertificationDao` 직무별 조회 (`COMMON` 포함) | B | ✅ |
| FR-111 공통 실패 처리 | E (화면 표시는 A) | ✅ |
| 설문 재응답 정책 | C | ✅ |
| 수정 메서드 `void` → `int` (특이사항 ①) | B | 완료 체크에 점수를 붙일 때 |
| 점수 요약 행 생성 | 점수 기능 담당 | ❌ 이후 |

---

## 7. 2주차 완료 기준

10/2(금)에 아래가 되면 2주차 완료입니다.

- **희망 직무가 없는 사용자**: 가입 → 프로필 → 설문 → 후보 선택 → 격차 분석 → 로드맵까지 **막힘 없이 한 바퀴**
- **희망 직무가 있는 사용자**: 프로필 → 격차 분석 → 로드맵
- **워크넷이나 LLM이 끊겨도** 화면이 멈추지 않고 캐시 결과나 안내 문구로 대체 (FR-111·112)
- 모든 화면이 공통 헤더·푸터·CSS를 사용
- 새 서비스마다 테스트, 재수집 배치는 **연속 두 번 실행** 테스트
- 2주차 개발일지 작성

임베딩이 늦어져서 정확 일치 매칭으로 10/2(금)를 맞아도 **2주차 목표는 달성**입니다. 임베딩 교체는 3주차 초에 해도 됩니다.
