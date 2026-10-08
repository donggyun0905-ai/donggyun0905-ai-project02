# 스펙 오디세이 (Spec Odyssey)

취업 준비생의 스펙을 진단하고, 목표 직무까지 가는 순서 있는 로드맵(여정)을 제시하는 웹 서비스입니다.
단순 진단으로 끝나지 않고 "다음에 뭘 해야 하는지"를 알려주고 매일 걷게 만드는 것이 핵심입니다.

- 범위: IT 계열 직무 한정 (백엔드 / 프론트 / 데이터 / DevOps / 보안 / PM)
- 기간: 4주 단기 프로젝트 (학습 목적)
- 핵심 여정 루프: 가입 → 프로필 입력 → 격차 분석 → 로드맵 제시 → 대시보드

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 언어 / 런타임 | Java 17, JSP/Servlet(`jakarta.*`), **Tomcat 10.1 / 11 둘 다 지원** |
| DB | MySQL 8.0+ (`utf8mb4`), 커넥션 풀 HikariCP |
| 빌드 | Maven |
| AI 분석 | 하이브리드 — 격차 분석은 규칙기반 SQL, 자연어 생성은 LLM API |
| 임베딩 | 로컬 생성 — DJL + ONNX Runtime, ko-sroberta-multitask (768차원) |

## 현재 구현된 기능

- **회원**: 3단계 가입(기본 정보 → 추가 정보 → 동의) / 로그인 / 로그아웃(POST) / 비밀번호 변경 / **복구 코드로 비밀번호 재설정** / 회원 탈퇴(**30일 유예** — 그 안에 다시 로그인하면 탈퇴 취소, 지나면 개인정보 정리 후 같은 아이디로 재가입 가능)
- **프로필**: 기본정보·보유 스펙·학력·기술 스택·이력서·자소서·AI 활용 기록 ·
  프로젝트 — 목록에서 간단히 추가하고, `/profile/projects/edit`에서 회고·기술 활용 설명서·제출 서류·증빙 파일까지
  전부 입력한다(로드맵 PROJECT 단계 제출과 같은 입력칸을 쓴다. 다른 점은 README·실행 화면 캡처를 필수로 받지 않는 것뿐)
- **직무 찾기(설문)** → **격차 분석** → **로드맵**: 기술별 사다리(입문→핵심→심화→전문가), 복습·프로젝트/글 업데이트·트렌딩 학습이 이어 붙는 "끝없는 로드맵", 점수는 직무 사다리 총점 기준으로 정규화
- **일일 미션**(코드 제출 컴파일 확인, 연속 풀이 보너스) / **점수·등급** / **대시보드** / **데이터 인사이트** / **D-day** / **서류 보관함** / **공유 링크(면접관 열람)** / **자소서 첨삭** / **스펙 아카이브**(상위 티어 팁 게시판)
- **관리자 화면**(`user_type = 'ADMIN'` 계정 전용): `/admin` 점수·복습 주기 규칙 편집 · `/admin/job-skill-trend` 직무 기술 트렌드 재집계 ·
  `/admin/articles` 신고된 기술 아티클 내리기·복구 · `/admin/users` 회원 검색·프로필 수정·비밀번호 재설정·탈퇴 처리 ·
  `/admin/roadmap` 회원 로드맵 단계 완료/취소/삭제(점수는 건드리지 않음) · `/admin/reference` 기준 데이터(직무·기술·자격증·기술 별칭·직무 요구 기술) 추가·수정
- 처음 설문을 하기 전에는 **설문과 프로필만** 열립니다. 자소서 첨삭·프로필을 뺀 지원자 화면에는 **좌우 고정 위젯**(왼쪽: 일일 미션·최근 서류·한눈에 보기(D-day·지금 할 일·나의 등급), 오른쪽: 목표 직무에 맞춘 트렌드 기술·연습장)이 붙습니다.
- 화면 아이콘은 이모지가 아니라 `css/icons.css`의 선 아이콘(`<span class="ic ic-이름">`)을 씁니다.

개발 과정·결정 사항은 [`개발일지/`](개발일지/)에 날짜별로 정리돼 있습니다.

## 디렉토리 구조

```
src/main/java/com/specodyssey/
  ├── controller/   서블릿 (요청 받기 + 응답만)
  ├── service/      비즈니스 로직
  ├── dao/          DB 접근 (SQL은 여기에만)
  ├── dto/          데이터 전달 객체
  └── util/         DB 커넥션, 비밀번호 해시(Argon2id) 등 공통 유틸
src/main/webapp/
  ├── WEB-INF/
  │   ├── views/    JSP (직접 접근 불가)
  │   └── web.xml
  └── index.jsp
docs/
  ├── requirements.md   요구사항 명세서
  └── db-design.md      DB 설계 및 ERD, 복합 UNIQUE 목록, 설계 판단 근거
sql/
  ├── 01~03    스키마 (03 = 확장 스키마, 점수 규칙·게시판 테이블 포함)
  ├── 04~11    시드 데이터 (직무·기술·자격증·설문·기술 별칭·점수 규칙 기본값)
  ├── 12~30    이미 만든 DB에 덧붙이는 변경(ALTER·데이터 보정) — 아래 "이미 DB가 있다면" 참고
  │            (19_seed_skill_alias_more·23_seed_certification_common은 새 DB에도 필요한 시드)
  └── do-not-run/   ⚠ 실행 금지(모든 데이터를 지우는 스냅샷, 약한 비밀번호의 테스트 계정 시드)
```

## 실행 방법

### 요구사항

- JDK 17
- Tomcat **10.1 또는 11** (서블릿 패키지가 `jakarta.servlet.*`이라 9 이하에서는 동작하지 않습니다). 두 버전은 JSP가 `record`를 읽는 규칙이 다릅니다(10.1은 `getX()`만, 11은 `x()`만 찾습니다). 그래서 앱 시작 때 `RecordElResolver`를 등록해, record면 `x()` → `getX()` → `isX()` 순서로 찾아 두 버전에서 같은 값이 나오게 했습니다(2026-10-07). 이제 record에 `getX()`와 `x()`를 굳이 둘 다 두지 않아도 됩니다. 기존에 둘 다 둔 record는 그대로 둬도 됩니다
- MySQL 8.0 이상
- Maven — 따로 설치하지 않아도 됩니다. 프로젝트에 Maven Wrapper(`mvnw`)가 들어 있어 첫 실행 때 Maven을 자동으로 받습니다.

### 1. DB 준비

```sql
CREATE DATABASE spec_odyssey CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**새 DB라면** 아래 순서로 한 번씩만 실행합니다. 한글이 깨지지 않게 **`--default-character-set=utf8mb4`를 꼭 붙이세요.**
(이 순서로 만든 DB는 현재 개발 DB와 테이블·컬럼이 같고, 설문 문항 18개·직무 18개·기술 163개·자격증(어학 포함) 등 기본 데이터가 들어갑니다.
`21`처럼 01~03에 이미 반영된 변경은 새 DB에서는 돌리지 않습니다.)

```bash
for f in 01_schema 02_seed 03_schema_extended 04_seed_extended 04_seed_skills \
         05_seed_survey 09_schema_skill_alias 10_seed_skill_alias 11_seed_skill_alias_english \
         19_seed_skill_alias_more 23_seed_certification_common; do
  mysql -u <user> -p --default-character-set=utf8mb4 spec_odyssey < sql/$f.sql
done
```

**이미 DB가 있다면** 아직 안 돌린 변경만 번호 순서대로 실행합니다(대부분 한 번만 실행해야 하는 `ALTER`라서, 이미 적용했는지 파일 맨 위 설명을 먼저 읽으세요).
최근 것: `15_schema_project_link`(프로젝트 기타 링크) · `16_alter_users_recovery_code`(**없으면 로그인부터 `Unknown column 'recovery_code_hash'` 오류**) ·
`17_schema_scoring_rule`(점수·주기 규칙, 없어도 기본값으로 동작) · `18_fix_withdrawn_login_id`(**21을 적용한 뒤에는 실행 금지** — 탈퇴 유예 중인 아이디까지 비움) · `19_alter_tech_article_spec_archive`(스펙 아카이브) ·
`19_seed_skill_alias_more`(기술 별칭 보강, 여러 번 실행해도 안전) · `20_alter_attachment_file_data`(스펙 아카이브 사진을 DB에) ·
`21_alter_users_withdraw_requested_at`(탈퇴 30일 유예, **없으면 로그인부터 `Unknown column 'withdraw_requested_at'` 오류**) ·
`22_strip_emoji_from_roadmap_reason`(로드맵 단계 설명의 이모지 앞머리 정리, 여러 번 실행해도 안전) · `23_seed_certification_common`(TOEIC·TOEIC Speaking·OPIc·웹디자인개발기능사, 여러 번 실행해도 안전) ·
`24_schema_notification`(헤더 알림 — 새 DB도 실행. 없으면 알림 버튼만 안 보이고 나머지는 동작) ·
`26_alter_users_is_test_simulation`(테스트 계정 표시 + 시뮬레이션 진행 상태 — 01·03에도 반영돼 새 DB에서는 불필요. 맨 아래 UPDATE가 테스트용 아이디들을 `is_test = TRUE`로 표시하는데, 그 계정은 시뮬레이션 초기화로 **물리 삭제**될 수 있으니 실제로 쓰는 계정이 섞였는지 먼저 확인) ·
`27_alter_simulation_target_score`(시뮬레이션을 "70일 고정"에서 "목표 점수에 닿을 때까지"로) ·
`28_alter_share_link_scope_age`(공유 링크 나이 공개 — **없으면 공유 링크·면접관 화면이 `Unknown column 'scope_age'` 오류**) ·
`29_schema_admin_audit_log`(관리자 감사 로그 — 새 DB도 실행. 없으면 관리자 화면에서 바꿀 때 기록이 안 남고 감사 로그 탭이 오류) ·
`30_alter_share_link_scope_activity`(공유 링크 활동 내역 공개 — 03에도 반영돼 새 DB에서는 불필요. **없으면 공유 링크·면접관 화면이 `Unknown column 'scope_activity'` 오류**) ·
`31_alter_interviewer_view_upgrade`(면접관 뷰 보강 — 경험 기간·팀 규모/역할·학력 테이블·공유 범위 2종·면접관 검토 상태/평점/메모. 01·03에도 반영돼 새 DB에서는 불필요. **없으면 프로필·공유 링크·면접관 화면이 `Unknown column` 오류**) ·
`32_alter_documents_file_data`(서류 파일 내용을 DB에 저장 — 03에도 반영돼 새 DB에서는 불필요. **없으면 서류 업로드·내려받기가 `Unknown column 'file_data'` 오류**. 예전에 디스크에 올린 서류는 서버를 켤 때 자동으로 DB로 옮겨진다) ·
`33_schema_companion_device`(데스크톱 캐릭터 연결 — 03에도 반영돼 새 DB에서는 불필요. **없으면 프로필의 캐릭터 켜기가 오류**) ·
`34_schema_skill_prerequisite`+`35_seed_skill_prerequisite`(기술 선수관계 — 로드맵 순서를 위상 정렬로 정한다. 34는 03에도 반영돼 새 DB에서는 불필요, 35는 새 DB도 실행. 없으면 로드맵이 예전처럼 중요도 순서만 쓴다) ·
`36_schema_skill_review_schedule`(간격 반복 복습 — 복습 주기를 기술마다 SM-2로 잡는다. 03에도 반영돼 새 DB에서는 불필요. **없으면 복습 완료가 `Table 'SKILL_REVIEW_SCHEDULE' doesn't exist` 오류**).
> 25번은 없습니다 — 알림(seongwon)과 시뮬레이션(donghyeon)의 번호가 24로 겹쳐 시뮬레이션 쪽을 26·27로 옮겼습니다.
> 나이 공개(seongwon)도 원래 22번이었는데 로드맵 이모지 정리와 겹쳐 28번으로 옮겼습니다.
`05_seed_survey`도 다시 실행하면 새 문항(18문항 중 없는 것)만 추가됩니다(같은 문구는 건너뜀).

> ⚠ **`sql/do-not-run/`의 파일은 데이터가 든 DB에서 실행하지 마세요.** 스냅샷은 모든 테이블을 지우고 빈 테이블로 다시 만듭니다.

### 2. 설정 파일 (.env)

DB 접속 정보·API 키·업로드 폴더는 소스에 하드코딩하지 않습니다. 둘 중 편한 방법으로 설정하세요.
`DBUtil`(DB)·`FileStorageUtil`(업로드 폴더)·`AppConfig`(API 키)가 모두 같은 규칙으로 읽습니다.

**방법 A — `.env` 파일 (추천, IntelliJ에서 바로 실행하고 싶을 때 편합니다)**

```bash
cp src/main/resources/.env.example src/main/resources/.env
```

복사한 `src/main/resources/.env`를 열어 실제 값으로 채웁니다. 이 파일은 `.gitignore`(`*.env`)에 걸려 있어 커밋되지 않으니 각자 로컬 값을 채우면 됩니다(팀원끼리 공유 금지 — 특히 비밀번호).
빌드 시 `WEB-INF/classes`에 들어가므로 IntelliJ·VS Code·`mvn package` 어느 쪽으로 실행해도 같은 값을 읽습니다. 값을 고쳤으면 다시 빌드하세요.

**방법 B — 환경변수**

`.env`에 값이 없을 때는 같은 이름의 환경변수로 대체됩니다 (CI 등에서 유용).

| 키 | 예시 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/spec_odyssey?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | (본인 MySQL 비밀번호) |
| `UPLOAD_DIR` | (선택) `C:/spec-odyssey-uploads` — 없으면 `<홈>/spec-odyssey-uploads`. 2026-10-07부터 새 서류는 DB에 저장하고, 이 폴더는 그 전에 올린 서류를 읽을 때만 쓴다 |
| `DB_POOL_SIZE` | (선택) DB 커넥션 풀 최대 크기, 기본 10 — (서버 수 × 값)이 DB `max_connections`를 넘지 않게 |
| `ADMIN_LOGIN_ID` | (선택) 관리자 로그인 아이디, 기본 `admin` — **그 아이디로 먼저 가입**해 두세요(아래 참고) |
| `ENABLE_TEST_SHORTCUT` | (선택) 로드맵 `[TEST] 파일 없이 통과` 버튼, 기본 켜짐 — **운영 배포에서는 `false`** |
| `WORK24_*_API_KEY` | 고용24 Open API 인증키 6종 — 키 이름은 `.env.example` 참고 |

`.env`와 환경변수가 둘 다 있으면 `.env` 값이 우선합니다. 둘 다 없으면 `DBUtil`이 기동 시점에 바로 에러를 던집니다 (fail-fast).

### 관리자 계정과 운영 배포 체크리스트

- 관리자는 `USERS.user_type = 'ADMIN'`인 계정입니다(2026-10-06 변경 — 전에는 로그인 아이디가 `ADMIN_LOGIN_ID`와 같은 계정이었습니다).
  `user_type`은 면접관 계정(TD-4) 때 이미 자유 값으로 열어 둔 컬럼이라 스키마 변경 없이 값만 추가했습니다.
  만드는 법: 평소처럼 **회원가입한 뒤** 그 계정의 `user_type`을 `ADMIN`으로 바꿉니다 — `UPDATE USERS SET user_type = 'ADMIN' WHERE login_id = '<아이디>' AND is_deleted = FALSE;`
  아이디만 맞으면 관리자가 되던 예전 방식과 달리, 가입이 열려 있어도 DB를 고칠 수 있는 사람만 관리자를 만들 수 있습니다.
  관리자 계정은 좌우 위젯·알림·설문 제한을 받지 않고, `/admin/` 밖의 지원자 화면으로 가면 `/admin`으로 돌려보냅니다(RoleFilter).
- 운영 `.env`: `ENABLE_TEST_SHORTCUT=false`, 필요 시 `DB_POOL_SIZE`, API 키들. HTTPS 뒤에서 운영하면 세션 쿠키에 Secure가 자동으로 붙습니다.
- 배포 직후 첫 화면은 폼 토큰(CSRF)이 새로 만들어져 "페이지가 만료됨"이 한 번 뜰 수 있습니다 — 새로고침하면 됩니다.
- **서버 시간대**: 일부 시각(완료 시각·복습 주기 계산 등)은 서버 JVM의 기본 시간대를 씁니다. 한국 시간 기준으로 맞추려면 Tomcat을 `-Duser.timezone=Asia/Seoul`로 띄우세요(`CATALINA_OPTS`). DB 접속 URL은 이미 `serverTimezone=Asia/Seoul`입니다.
- JSP를 고친 뒤 화면이 그대로면 Tomcat의 `work` 폴더와 이전 배포본을 지우고 다시 배포하세요(JSP는 한 번 변환해 두고 다시 씁니다).

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
3. SERVERS 패널 → `Community Server Connector` 우클릭 → **Create New Server** → **No, use server on disk** → Tomcat 10.1 또는 11 폴더 선택
4. 만든 서버 우클릭 → **Add Deployment** → `target/spec-odyssey.war` 선택 → **Start Server**
5. `http://localhost:8080/spec-odyssey/` 접속 (Tomcat 포트가 80이면 `http://localhost/spec-odyssey/`)

코드를 고친 뒤에는 다시 빌드하고, 서버 우클릭 → **Publish Server (Full)** 로 반영합니다.

> Tomcat을 80 포트로 쓸 때 Windows의 IIS가 켜져 있으면 포트 충돌로 403이 뜹니다. IIS를 끄거나 8080을 쓰세요.

## 테스트

```bash
./mvnw test        # Windows: mvnw.cmd test
```

테스트는 **`.env`가 가리키는 실제 DB에 접속해서** 돌아갑니다(가짜 DB가 아닙니다). 테스트가 만드는 데이터는 `test_` 접두사로 만들고 끝나면 지우지만, 팀이 함께 쓰는 공유 DB에서는 실행하지 말고 **본인 로컬 DB**에서 돌리세요.
화면을 열어 LLM이 만든 벤치마크 행(`JOB_BENCHMARK_SPEC`)이 쌓여 있으면 `JobBenchmarkSpecServiceTest`가 실패할 수 있습니다 — 그 테이블을 비우고 다시 돌리면 됩니다.

## 참고 문서

- [`docs/dev-environment-setup.html`](docs/dev-environment-setup.html) — IDE별(IntelliJ·VS Code) 실행 환경 설정 가이드, 자주 나는 문제 해결
- [`docs/requirements.md`](docs/requirements.md) — 요구사항 명세서 (FR/NFR 번호의 출처)
- [`docs/db-design.md`](docs/db-design.md) — 테이블 정의, ERD, 복합 UNIQUE 목록, 설계 판단 근거
- [`claude.md`](claude.md) — 프로젝트 팀 규칙 (환경, 명명 규칙, 코드/보안 규칙)
