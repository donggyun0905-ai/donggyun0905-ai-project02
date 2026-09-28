# 스펙 오디세이 (Spec Odyssey) — 프로젝트 규칙

> Claude Code는 매 세션 이 파일을 먼저 읽습니다. 팀 규칙이 바뀌면 여기를 고치세요.
> 개인 설정(내 역할 등)은 각자 `CLAUDE.local.md`에 두고 커밋하지 않습니다.

---

## 환경 (확정)

| 항목 | 값 |
| --- | --- |
| Tomcat | **10.1.x 이상** → 서블릿 패키지는 `jakarta.servlet.*` |
| Java | 17 이상 (Tomcat 10.1은 Java 11+ 필요, 17 권장) |
| MySQL | 8.0 이상 · `utf8mb4` |
| 빌드 도구 | Maven / Gradle / 수동 — ( 팀에서 채울 것 ) |
| IDE | ( 팀에서 채울 것 ) |

**팀원 전원이 Tomcat 10.1 이상인지 확인하세요.** 한 명이라도 9를 쓰면 그 사람 환경에서만
전체가 컴파일 실패합니다. 9와 10은 패키지명이 달라 코드 호환이 되지 않습니다.

---

## ⚠️ Jakarta 전환 주의사항 — 인터넷 예제 대부분이 javax 기준입니다

Tomcat 10부터 `javax.*` → `jakarta.*`로 바뀌었습니다. 검색해서 나오는 JSP/Servlet 예제는
대부분 구버전(javax) 기준이라 **그대로 쓰면 안 됩니다.** 아래를 지키세요.

**1. import 문**

```java
// 올바름 (Tomcat 10+)
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.annotation.WebServlet;

// 틀림 — Tomcat 10에서 동작하지 않음
import javax.servlet.http.HttpServlet;
```

**2. JSTL taglib URI가 바뀌었습니다** — 이게 가장 많이 걸리는 지점입니다.

```jsp
<%-- 올바름 (JSTL 3.0) --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<%-- 틀림 — 구버전 URI --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
```

JSTL 라이브러리도 3.0 이상을 써야 합니다
(`jakarta.servlet.jsp.jstl-api` 3.0 + `jakarta.servlet.jsp.jstl` 구현체 3.0).
구버전 `jstl-1.2.jar`를 넣으면 동작하지 않습니다.

**3. 파일 업로드는 Servlet Part API를 쓰세요**

Commons FileUpload 1.x는 javax 기반이라 Tomcat 10에서 못 씁니다.
`@MultipartConfig` + `request.getPart()`를 사용하세요. 표준 API라 의존성도 줄어듭니다.

**4. web.xml 스키마**

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">
```

**5. 라이브러리 선택 기준**

의존성을 추가할 때 jakarta 호환 버전인지 먼저 확인하세요.
MySQL Connector/J, Gson, Jackson은 서블릿 API와 무관해서 그대로 쓸 수 있습니다.

---

## 프로젝트 개요

취업 준비생의 스펙을 진단하고, 목표 직무까지 가는 **순서 있는 로드맵(여정)** 을 제시하는 웹 서비스.
단순 진단이 아니라 "다음에 뭘 할지"를 알려주고 매일 걷게 만드는 것이 정체성.

- 범위: **IT 계열 직무 한정** (백엔드/프론트/데이터/DevOps/보안/PM)
- 기간: 4주 (단기 프로젝트, 학습 목적)
- 핵심 여정 루프: 가입 → 프로필 입력 → 격차 분석 → 로드맵 제시 → 대시보드

---

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 언어/런타임 | Java 17, JSP/Servlet (jakarta.*), Tomcat 10.1+ |
| DB | MySQL 8.0+ (utf8mb4) |
| JSON 파싱 | Gson 또는 Jackson |
| AI 분석 | 하이브리드 — 격차 분석은 규칙기반 SQL, 자연어 생성은 LLM API |
| 임베딩 | **로컬 생성** — DJL + ONNX Runtime, ko-sroberta-multitask (768차원). 세팅이 막히면 임베딩 API로 전환 (`SKILL.embedding_model`로 구분) |
| 파일 업로드 | Servlet Part API (`@MultipartConfig`) |

딥러닝 자체 학습은 범위 밖. 프론트엔드 프레임워크(React 등) 사용하지 않음.

---

## 디렉토리 구조

```
src/main/java/com/specodyssey/
  ├── controller/     서블릿 (요청 받기 + 응답만)
  ├── service/        비즈니스 로직
  ├── dao/            DB 접근 (SQL은 여기에만)
  ├── dto/            데이터 전달 객체
  └── util/           DB 커넥션, 암호화, 공통 유틸
src/main/webapp/
  ├── WEB-INF/
  │   ├── views/      JSP (직접 접근 불가)
  │   └── web.xml
  ├── css/  js/  img/
docs/
  ├── requirements.md 요구사항 명세서
  ├── db-design.md    DB 설계 및 ERD
  ├── dao-guide.md    DAO 사용 규칙, 보류 항목
  └── role-plan.md   2주차 역할 분담과 계약
sql/
  ├── 01_schema.sql         테이블 생성
  ├── 02_seed.sql           초기 데이터 (수정 금지)
  ├── 04_seed_skills.sql    스킬·직무 요구 기술·별칭 (D)
  ├── 05_seed_survey.sql    직무 발굴 설문 (C)
  └── 06_seed_benchmark.sql 직무 벤치마크 (B)
```

---

## 명명 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 테이블 | UPPER_SNAKE_CASE | `USER_SKILLS` |
| 컬럼 | snake_case | `profile_updated_at` |
| FK 컬럼 | `참조테이블단수_id` | `job_id`, `skill_id` |
| Java 필드 | camelCase | `profileUpdatedAt` |
| 클래스 | PascalCase + 역할 접미사 | `UserDao`, `GapAnalysisService` |
| JSP | kebab-case | `gap-analysis.jsp` |

---

## DB 규칙

**공통 컬럼** — 모든 테이블에 아래 3개를 반드시 포함.

```sql
created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
```

- 물리 삭제 금지. `is_deleted = TRUE`로 논리 삭제하고, 조회 시 항상 `is_deleted = FALSE` 조건을 건다.
- 모든 FK에 인덱스를 건다.
- **복합 UNIQUE를 빠뜨리지 말 것.** `docs/db-design.md`의 "복합 UNIQUE 제약" 표가 목록.
  특히 `JOB_REQUIRED_SKILL(job_id, skill_id)`와 `JOB_SKILL_TREND(job_id, skill_id, period_ym)`은
  없으면 주간 재수집 때마다 같은 행이 계속 쌓인다.
- 문자셋은 `utf8mb4` / `utf8mb4_unicode_ci`.
- 금액·점수는 `DECIMAL`, 유사도는 `DECIMAL(5,4)`.

---

## 코드 규칙

- **SQL은 DAO에만.** 서블릿이나 JSP에 SQL이 들어가면 안 된다.
- **JSP에 비즈니스 로직 금지.** 스크립틀릿(`<% %>`)으로 계산하지 말고 JSTL/EL로 출력만.
- **PreparedStatement 필수.** 문자열 연결로 SQL을 만들지 않는다.
- try-with-resources로 Connection/Statement/ResultSet을 닫는다.
- 여러 테이블을 함께 변경하면 트랜잭션(`TransactionUtil`)으로 묶는다 (예: 격차 분석 저장 + 로드맵 생성).
- AI 호출은 **반드시 서버(서블릿)에서**. 클라이언트 JS에서 직접 호출 금지.
- 외부 API(LLM, 임베딩, 워크넷) 호출은 **타임아웃을 직접 지정**한다.
- LLM 응답은 JSON으로 받고 파싱 실패를 예외 처리한다. 실패 시 캐시된 직전 결과로 대체 (FR-111).
  재시도는 429·5xx·-1만, 400·401은 재시도하지 않는다.
- 새 서비스에는 테스트를 함께 작성한다.

---

## 협업 규칙 (충돌 방지)

| 파일 | 규칙 |
| --- | --- |
| `header.jsp`, `footer.jsp`, 공통 CSS | **A 담당만 수정.** 메뉴 추가가 필요하면 A에게 요청 |
| `sql/02_seed.sql` | **수정 금지.** 시드는 담당자별 파일로 분리 (위 디렉토리 구조 참고) |
| `web.xml` | 수정 금지. 에러 페이지와 인코딩은 이미 설정됨 |
| DAO | 메서드 **추가는 자유.** 기존 메서드 시그니처를 바꾸면 PR에 사용처를 적고 `docs/dao-guide.md`도 같이 갱신 |
| `SessionFilter` 공개 경로 | 변경 시 **리뷰어 2명** (보안 경계) |
| `JobRequiredSkillDao` | 쓰기는 D, 읽기는 A |

**공유 인터페이스** — 여러 팀원이 이 시그니처에 맞춰 개발합니다. `docs/role-plan.md` 3절과
**정확히 같게** 유지하고, 바꿔야 하면 코드를 고치기 전에 먼저 물어볼 것.

- `SkillMatcher` (`com.specodyssey.service`)
- `LlmClient` (`com.specodyssey.util`)
- `RoadmapService.createRoadmap(Connection, ...)` — 격차 분석 트랜잭션 안에서 호출

작업 하나 = 브랜치 하나 = PR 하나. 작게 자주 병합한다.

---

## 보안

- 비밀번호는 해시 + 솔트 저장. 평문 저장 절대 금지.
- **API 키를 소스에 하드코딩하지 않는다.** `web.xml` context-param 또는 환경변수로 분리.
- 키가 들어간 파일은 `.gitignore`에 넣는다.
- 세션으로 본인 데이터만 접근. 다른 사용자 id를 파라미터로 받아 조회하지 않는다.
- 면접관 공유 링크 토큰은 추측 불가능한 랜덤 문자열(`SecureRandom`), 읽기 전용.

---

## 하지 말 것

- 채용 플랫폼 코딩테스트 문제 지문 크롤링 (저작권 위반)
- 워크넷 크롤링 (공식 API가 있으므로)
- 대화형 AI 멘토 챗봇 구현 (이번 범위에서 보류)
- 요청하지 않은 테이블·기능 임의 추가
- `javax.servlet.*` import 사용 (Tomcat 10+는 `jakarta.servlet.*`)
- 구버전 JSTL URI(`http://java.sun.com/jsp/jstl/core`) 사용
- 스키마 임의 변경 — `docs/db-design.md`가 기준이며, 바꿔야 하면 먼저 물어볼 것

---

## 현재 작업 단계 (2주차 · 9/28 ~ 10/2)

1주차(회원/인증 + 프로필 CRUD)는 완료.

**목표: 직무 발굴 → 격차 분석 → 로드맵 여정이 한 바퀴 동작** (FR-31·32·34·38·39, FR-111·112)

- 역할 분담, 일정, 1일차 계약, 완료 기준: `docs/role-plan.md`
- 화면 흐름: `/profile` → (`/discover`) → `/analysis?jobId=…` → `/roadmap`
- 완료 기준 핵심: 워크넷이나 LLM이 끊겨도 화면이 멈추지 않고 캐시 결과나 안내 문구로 대체

---

## 참고 문서

- `docs/requirements.md` — 요구사항 명세서 (FR/NFR 번호의 출처)
- `docs/db-design.md` — 테이블 정의, ERD, 복합 UNIQUE 목록, 설계 판단 근거
- `docs/dao-guide.md` — DAO 사용 규칙, 특이사항, 보류 항목
- `docs/role-plan.md` — 2주차 역할 분담과 인터페이스 계약

코드에 요구사항 번호를 주석으로 남기면 추적이 쉽다. 예: `// FR-37 프로필 변경 시 재분석`
