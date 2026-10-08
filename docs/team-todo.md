# 넘기기 전에 할 일 (2026-10-08 병합 기준)

> 10/7~10/8 네 브랜치(kangdain · seongwon · donghyeon · donggyun0905)를 모두 합친 뒤
> **코드로는 끝났지만 사람이 해야 남는** 세 가지. 각 항목에 "왜"와 "어디를 보면 되는지"를 적었다.
> 기준 커밋: `bc6d3c4` · `mvn clean test` 1030개 통과.

---

## 1. `SessionFilter` 공개 경로에 `/api/companion/` 추가 — **리뷰어 한 명 더 필요**

### 왜 리뷰어가 2명인가

`claude.md` 협업 규칙 표에 이 줄이 있다.

| 파일 | 규칙 |
| --- | --- |
| `SessionFilter` 공개 경로 | 변경 시 **리뷰어 2명** (보안 경계) |

`SessionFilter`는 `/*` 전체에 걸려 있고, `PUBLIC_PATHS` / `PUBLIC_PREFIXES`에 **나열된 것만**
로그인 없이 통과시킨다. 이 배열에 경로를 넣는 순간 그 아래 모든 요청이 로그인 검사를 건너뛴다.
오타 하나로 범위가 넓어진다 — 예를 들어 `/api/companion/`이 아니라 `/api/`로 적으면
사이트의 모든 API가 인증 없이 열린다. 프로젝트에서 "2명"을 요구하는 유일한 지점이다.

### 무엇이 바뀌었나

`src/main/java/com/specodyssey/controller/SessionFilter.java`

```java
private static final String[] PUBLIC_PREFIXES = {
        "/css/", "/js/", "/img/", "/image/", "/share/", "/api/companion/"
};
```

데스크톱 캐릭터(exe)는 브라우저가 아니라서 쿠키 세션이 없다. 그래서 세션 대신
캐릭터 전용 토큰으로 사용자를 확인한다. 필터를 통과시키되 서블릿이 직접 검사하는 구조.

### 1차 검토 결과 (donggyun0905)

다음 리뷰어는 같은 자리를 보면 된다.

- **토큰 없이 부를 수 있는 건 `/token` 하나뿐.** 나머지 7개 액션(`/messages` `/latest`
  `/download` `/note` `/note-save` `/read` `/disconnect`)은 `switch` 앞에서
  `authService.authenticate(bearer(req))`가 `null`이면 401로 끊는다.
  → `CompanionApiServlet.doPost` 70~75줄
- **토큰 발급은 웹 로그인 사용자만.** 일회용 연결 코드로만 교환된다.
  `SecureRandom` 24바이트(192비트) · **유효 60초** · 1회 사용 · DB에는 SHA-256 해시만.
  무작위 대입이 통하지 않는다. → `CompanionAuthService` 25~65줄
- **사용자 식별에 파라미터를 믿지 않는다.** 토큰에서 꺼낸 `device.getUserId()`만 쓴다.
  `/read`는 `notificationService.open(id, userId)`라 남의 알림 id를 넣어도 아무것도 안 바뀐다.
- **`/download`의 `Content-Disposition` 헤더 주입 불가.** 파일명이 업로드한 사람이 보낸
  이름이 아니라 서버가 고정한 `"SpecOdysseyCompanion-Setup.exe"`다.
  → `AdminCompanionServlet` 저장 시점에 고정
- 관리자 업로드는 `AdminSession.isAdmin` 게이트 + 감사 로그 + 200MB 상한.

### 사람이 기억하지 않아도 되게 — 빌드에서 막는다 (2026-10-08 추가)

"조심하자"는 다음에 또 잊는다. 그래서 `SessionFilterPublicPathTest`로 목록을 잠가 뒀다.

- **목록 잠금.** 테스트 안에 지금 합의된 공개 경로가 그대로 적혀 있고, 실제 목록과 한 글자라도
  다르면 빌드가 깨진다. 고치려면 테스트도 같이 고쳐야 하므로 PR diff에 "보안 경계를 건드렸다"가
  반드시 드러난다 — 리뷰에서 놓칠 수가 없다.
- **위험한 뿌리 차단.** `/api/` `/admin/` `/profile/` `/companion/` 같은 한 칸짜리 뿌리는
  통째로 공개할 수 없다. 더 깊은 `/api/companion/`은 범위가 서블릿 하나라 괜찮다.
- **모양 검사.** `/`로 시작하고 `/`로 끝나야 한다. 끝의 `/`가 빠지면 `startsWith`가 옆 경로까지
  먹는다 — `"/share"`는 `/shared-secrets`도 통과시킨다.
- **서로 덮기 검사.** 넓은 접두사가 좁은 것을 이미 덮고 있으면 실수로 들어온 것으로 보고 알린다.
- **대표 경로 직접 확인.** `/dashboard` `/admin` `/api/other` 등이 여전히 막혀 있고,
  `/login` `/api/companion/token` 등은 열려 있는지 실제로 물어본다.

실제로 잡는지 확인했다. `"/api/companion/"`을 `"/api/"`로 바꿔 보니 6개 중 3개가 깨졌다
(목록 불일치 · 위험한 뿌리 · `/api/other`가 열림). 원래대로 되돌려 뒀다.

### 할 일

여전히 사람 한 명은 필요하다 — 테스트는 "바뀌었다"를 알릴 뿐 "이 변경이 옳다"는 판단은 못 한다.
강다인 또는 성원 중 한 명이 위 두 파일(`SessionFilter.PUBLIC_PREFIXES`,
`CompanionApiServlet.doPost`의 401 분기)을 보고 PR에 확인 한 줄을 남긴다. 5분이면 된다.

---

## 2. `header.jsp`가 담당 규칙 밖에서 수정됨 — **A 담당에게 공유**

### 규칙

| 파일 | 규칙 |
| --- | --- |
| `header.jsp`, `footer.jsp`, 공통 CSS | **A 담당만 수정.** 메뉴 추가가 필요하면 A에게 요청 |

`header.jsp`는 모든 화면이 include한다. 여기서 충돌이 나면 한 명이 아니라 전원이 막힌다.
그래서 수정 권한을 한 사람으로 묶어 뒀다.

### 무엇이 들어갔나

`src/main/webapp/WEB-INF/views/common/header.jsp` · `성장 도구` 그룹 맨 위 4줄 (donghyeon)

```jsp
<%-- 데스크톱 캐릭터 설치 파일 — 연결된 PC가 있으면 숨긴다 (CompanionNavFilter) --%>
<c:if test="${showCompanionDownload}">
<a href="${ctx}/companion/download"><span class="ic ic-download"></span> 캐릭터 내려받기</a>
</c:if>
```

`showCompanionDownload`는 `CompanionNavFilter`가 넣어 준다.
이미 설치한(= 연결된 PC가 있는) 계정에는 메뉴가 안 보인다.

### 할 일

**되돌릴 필요는 없다.** 내용 자체는 문제가 없다.
A 담당에게 "이미 들어가 있다"고 알려 주기만 하면 된다.

다만 앞으로 또 건드리지 않도록, 이 메뉴의 마크업·스타일·스크립트를
`common/companion-nav.jspf` 한 파일로 옮겼다 (2026-10-08). `header.jsp`에 남은 것은
include 한 줄뿐이라 **원래 4줄보다 오히려 줄었고**, 이 메뉴를 고칠 일은 이제 A의 파일을
건드리지 않는다. 다음에 메뉴를 추가할 사람은 A에게 요청하는 절차를 지킨다.

---

## 3. 마이그레이션 `sql/34` ~ `38` 적용

### 상황

**공유 DB에는 이미 전부 들어가 있다.** 지금 공유 DB로 띄우는 사람은 아무것도 안 해도 된다.
자기 PC에 **로컬 DB를 따로 쓰는 사람만** 돌려야 하고, 안 돌리면 해당 화면에서 500이 난다.

| 파일 | 내용 | 없으면 깨지는 곳 |
| --- | --- | --- |
| `34_schema_skill_prerequisite.sql` | `SKILL_PREREQUISITE` 테이블 | 로드맵 생성, 관리자 선수관계 탭 |
| `35_seed_skill_prerequisite.sql` | 선수관계 19쌍 초기 데이터 | 테이블은 있지만 비어서 위상 정렬이 아무 일도 하지 않는다 |
| `36_schema_skill_review_schedule.sql` | `SKILL_REVIEW_SCHEDULE` 테이블 | 복습 자기 평가(SM-2 간격 반복) |
| `37_alter_tech_article_fulltext.sql` | `TECH_ARTICLE`에 ngram FULLTEXT 인덱스 | 스펙 아카이브 글 검색 |
| `38_schema_companion_release.sql` | `COMPANION_RELEASE` + `COMPANION_RELEASE_CHUNK` | 관리자 캐릭터 업로드, 내려받기 |

### 지름길 — DB를 새로 만드는 경우

34 · 36 · 37 · 38은 **`sql/03_schema_extended.sql`에도 반영해 뒀다.**
(`SKILL_PREREQUISITE` 1154줄 · `SKILL_REVIEW_SCHEDULE` 1179줄 ·
`COMPANION_RELEASE` 1204줄 · `COMPANION_RELEASE_CHUNK` 1223줄 · FULLTEXT 인덱스 879줄)

- **DB를 처음부터 새로 만든다** → `01` ~ `03` 돌린 뒤 **`sql/35`만** 추가로 돌린다.
- **기존 DB를 계속 쓴다** → `34` → `35` → `36` → `37` → `38` 순서대로 돌린다.

`35`는 여러 번 돌려도 안전하다(기술을 이름으로 찾고 중복은 무시한다).
시드에 없는 기술이 섞여 있으면 그 줄만 조용히 건너뛴다(INNER JOIN).

---

## 참고 — 마이그레이션 번호 충돌이 6번 났다

`21` `24` `22` `29` `32` `33`에서 서로 다른 사람이 같은 번호를 썼다.
**새 `.sql`을 만들기 전에 `ls sql/` 로 가장 큰 번호를 먼저 확인하자.**
빌드 단계에서 번호 중복을 잡는 검사를 넣는 것도 남은 과제다.
