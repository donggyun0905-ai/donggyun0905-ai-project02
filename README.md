# 스펙 오디세이 (Spec Odyssey)

취업 준비생의 스펙을 진단하고, 목표 직무까지 가는 순서 있는 로드맵(여정)을 제시하는 웹 서비스입니다.
단순 진단으로 끝나지 않고 "다음에 뭘 해야 하는지"를 알려주고 매일 걷게 만드는 것이 핵심입니다.

- 범위: IT 계열 직무 한정 (백엔드 / 프론트 / 데이터 / DevOps / 보안 / PM)
- 기간: 4주 단기 프로젝트 (학습 목적)
- 핵심 여정 루프: 가입 → 프로필 입력 → 격차 분석 → 로드맵 제시 → 대시보드

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 언어 / 런타임 | Java 17, JSP/Servlet(`jakarta.*`), Tomcat 10.1+ |
| DB | MySQL 8.0+ (`utf8mb4`) |
| 빌드 | Maven |
| AI 분석 | 하이브리드 — 격차 분석은 규칙기반 SQL, 자연어 생성은 LLM API |
| 임베딩 | 로컬 생성 — DJL + ONNX Runtime, ko-sroberta-multitask (768차원) |

## 현재 진행 상황

**1주차 — 회원/인증 + 프로필 CRUD 완료**

- [x] `sql/01_schema.sql` — 회원/프로필 관련 8개 테이블 (FK·인덱스·복합 UNIQUE 포함)
- [x] `sql/02_seed.sql` — IT 직무 18개, 자격증 37개
- [x] DB 커넥션 유틸 (`DBUtil`) + DTO/DAO
- [x] 회원가입 / 로그인 / 로그아웃 / 세션 필터
- [x] 프로필 조회·수정 화면 (기본정보 + 보유 스펙 + 프로젝트 + 기술 스택)

격차 분석, 로드맵 생성, 대시보드 등 나머지 기능은 2주차 이후 범위입니다.

## 디렉토리 구조

```
src/main/java/com/specodyssey/
  ├── controller/   서블릿 (요청 받기 + 응답만)
  ├── service/      비즈니스 로직
  ├── dao/          DB 접근 (SQL은 여기에만)
  ├── dto/          데이터 전달 객체
  └── util/         DB 커넥션, 비밀번호 해시 등 공통 유틸
src/main/webapp/
  ├── WEB-INF/
  │   ├── views/    JSP (직접 접근 불가)
  │   └── web.xml
  └── index.jsp
docs/
  ├── requirements.md   요구사항 명세서
  └── db-design.md      DB 설계 및 ERD, 복합 UNIQUE 목록, 설계 판단 근거
sql/
  ├── 01_schema.sql
  └── 02_seed.sql
```

## 실행 방법

### 요구사항

- JDK 17
- Tomcat 10.1 이상 (서블릿 패키지가 `jakarta.servlet.*`이라 9 이하에서는 동작하지 않습니다)
- MySQL 8.0 이상
- Maven — 따로 설치하지 않아도 됩니다. 프로젝트에 Maven Wrapper(`mvnw`)가 들어 있어 첫 실행 때 Maven을 자동으로 받습니다.

### 1. DB 준비

```sql
CREATE DATABASE spec_odyssey CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u <user> -p spec_odyssey < sql/01_schema.sql
mysql -u <user> -p spec_odyssey < sql/02_seed.sql
mysql -u <user> -p spec_odyssey < sql/03_schema_extended.sql
```

### 2. DB 접속 정보 설정

DB 접속 정보는 소스에 하드코딩하지 않습니다. 둘 중 편한 방법으로 설정하세요.

**방법 A — `.env` 파일 (추천, IntelliJ에서 바로 실행하고 싶을 때 편합니다)**

```bash
cp src/main/resources/.env.example src/main/resources/.env
```

복사한 `src/main/resources/.env`를 열어 실제 값으로 채웁니다. 이 파일은 `.gitignore`(`*.env`)에 걸려 있어 커밋되지 않으니 각자 로컬 값을 채우면 됩니다(팀원끼리 공유 금지 — 특히 비밀번호).
### 2. 설정 파일 (config.properties)

**방법 B — 환경변수**

`.env` 파일이 없을 때는 아래 환경변수로 대체됩니다 (CI 등에서 유용).
DB 접속 정보·API 키는 소스에 하드코딩하지 않고 `config.properties`로 분리되어 있습니다.
루트의 `config.properties.example`을 복사해 **같은 위치(프로젝트 루트)에 `config.properties`** 로 만들고 값을 채우세요.
`.gitignore` 대상이라 커밋되지 않으며, `pom.xml` 설정으로 빌드 시 `WEB-INF/classes`에 들어가므로 IntelliJ·VS Code·`mvn package` 어느 쪽으로 실행해도 같은 값을 읽습니다.
같은 이름의 환경변수가 있으면 환경변수가 우선합니다.

| 키 | 예시 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/spec_odyssey?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | (본인 MySQL 비밀번호) |

`.env`와 환경변수가 둘 다 있으면 `.env` 값이 우선합니다. 둘 다 없으면 `DBUtil`이 기동 시점에 바로 에러를 던집니다 (fail-fast).

### 3. 빌드 & 배포

Maven Wrapper로 빌드합니다 (Maven 설치 불필요).

```bash
mvnw.cmd clean package -DskipTests   # Windows
./mvnw clean package -DskipTests     # macOS / Linux
```

생성된 `target/spec-odyssey.war`(또는 폴더 `target/spec-odyssey/`)를 Tomcat에 배포하고 기동하면 됩니다.

### 4. VS Code에서 실행

1. 확장 설치: `Extension Pack for Java`, `Community Server Connectors`(Red Hat)
2. 위 3번 명령으로 빌드
3. SERVERS 패널 → `Community Server Connector` 우클릭 → **Create New Server** → **No, use server on disk** → Tomcat 10.1 폴더 선택
4. 만든 서버 우클릭 → **Add Deployment** → `target/spec-odyssey.war` 선택 → **Start Server**
5. `http://localhost:8080/spec-odyssey/` 접속 (Tomcat 포트가 80이면 `http://localhost/spec-odyssey/`)

코드를 고친 뒤에는 다시 빌드하고, 서버 우클릭 → **Publish Server (Full)** 로 반영합니다.

> Tomcat을 80 포트로 쓸 때 Windows의 IIS가 켜져 있으면 포트 충돌로 403이 뜹니다. IIS를 끄거나 8080을 쓰세요.

## 참고 문서

- [`docs/dev-environment-setup.html`](docs/dev-environment-setup.html) — IDE별(IntelliJ·VS Code) 실행 환경 설정 가이드, 자주 나는 문제 해결
- [`docs/requirements.md`](docs/requirements.md) — 요구사항 명세서 (FR/NFR 번호의 출처)
- [`docs/db-design.md`](docs/db-design.md) — 테이블 정의, ERD, 복합 UNIQUE 목록, 설계 판단 근거
- [`claude.md`](claude.md) — 프로젝트 팀 규칙 (환경, 명명 규칙, 코드/보안 규칙)
