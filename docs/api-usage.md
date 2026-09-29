# 외부 API 활용 정리 (고용24 Open API)

> `docs/api_info/`의 API 명세 PDF 6개를 `db-design.md`·`requirements.md`와 대조해 정리한 문서.
> 어떤 API의 어떤 필드가 어느 테이블/FR로 들어가는지가 기준이다.
> 스키마 변경이 필요한 항목은 **확정이 아니라 팀 확인 대상**으로 표시했다.

---

## 요약

| API | 엔드포인트 | 응답 | 연결 테이블 / FR | 활용도 | 도입 시점 |
| --- | --- | --- | --- | --- | --- |
| 채용정보 | `callOpenApiSvcInfo210L01.do` | XML | `JOB_REQUIRED_SKILL`, `JOB_SKILL_TREND`, `DDAY_ALERT`(RECRUIT), `EXTERNAL_API_CACHE` / FR-44·47·71·113, TD-2 | ★★★ 핵심 | 2주차 |
| 직무정보 (표준직무기술서) | `callOpenApiSvcInfo215L01.do` | **JSON** | `JOB_REQUIRED_SKILL` 보완, `ROADMAP_STEP.reason` / FR-113·33 | ★★ | 2주차 |
| 학과정보 | `callOpenApiSvcInfo213L01.do` | XML | `USERS.major`, `SPEC_SCORE_HISTORY.major` / FR-21·45·38 | ★★ | **1주차** |
| 직업정보 | `callOpenApiSvcInfo212L01.do` | XML | `JOB`·`JOB_ALIAS` 시드 참고, `JOB_RECOMMENDATION.summary_json` / FR-35 | ★ | 1주차(시드 참고) |
| 강소기업 | `callOpenApiSvcInfo216L01.do` | XML | 해당 FR 없음 | ✕ | 사용 안 함 |
| 구직자취업역량 강화프로그램 | `callOpenApiSvcInfo217L01.do` | XML | 해당 FR 없음 | ✕ | 사용 안 함 |

공통 URL 접두어: `https://www.work24.go.kr/cm/openApi/call/wk/`

---

## 1. 채용정보 (210L01) — 메인 데이터 소스

TD-2의 "워크넷(고용24) Open API 수집"이 이 API다.

### 요청

필수: `authKey`, `callTp`(L: 목록 / D: 상세), `returnType=XML`, `startPage`(1~1000), `display`(최대 100)

이 프로젝트에서 쓸 만한 선택 파라미터:

| 파라미터 | 용도 |
| --- | --- |
| `keyword` | 직무 키워드 (예: 백엔드, 개발자). 다중 검색 가능 |
| `occupation` | 직종코드 — IT 직종으로 한정. 코드표는 공통코드 API 필요 |
| `career` | `N` 신입 / `E` 경력(`minCareerM`·`maxCareerM` 필수) / `Z` 무관 |
| `education` | 학력 코드 (`05` 대졸 4년 등) |
| `certLic` | 자격면허 코드 — 자격증별 요구 공고 수 집계용 |
| `coTp` | 기업형태 (`01` 대기업, `03` 벤처, `04` 공공기관, `05` 외국계, `09` 청년친화강소기업) |
| `regDate` | 등록일 범위 (`D-0`, `D-3`, `W-1`, `W-2`, `M-1`) — 주간 배치 수집용 |
| `major` | 전공코드 (3차 계열만) |

### 응답 (목록, `<wantedRoot>` → `<wanted>`)

`total`, `wantedAuthNo`(구인인증번호), `company`, `busino`, `indTpNm`, `title`, `salTpNm`, `sal`, `minSal`, `maxSal`, `region`, `holidayTpNm`, `minEdubg`, `maxEdubg`, `career`, `regDt`, `closeDt`, `infoSvc`, `wantedInfoUrl`, `wantedMobileInfoUrl`, 근무지 주소 4종, `empTpCd`, `jobsCd`, `smodifyDtm`

### 활용처

| 대상 | 방법 |
| --- | --- |
| `JOB_REQUIRED_SKILL` (TD-1) | 직무별 공고에서 기술 키워드 추출 → `SKILL` 임베딩 매칭 → `source='WORKNET'`, `is_estimated=false` |
| `JOB_SKILL_TREND` (FR-47) | 월 1회 스냅샷: 직무×기술별 언급 건수 → `mention_count`, 전체 공고 대비 비율 → `mention_ratio` |
| FR-113 판단 | `total = 0`이면 LLM 일반화 요구스펙으로 보완("예시적 추정") |
| `DDAY_ALERT` (FR-71) | `closeDt` → `alert_type='RECRUIT'` |
| `CERTIFICATION` 우선순위 | `certLic` 필터로 자격증별 요구 공고 수 → 로드맵 배치 근거 |
| 대기업 커버리지 확인 | `coTp=01` 조회 건수로 TD-2 "대기업 공고 부족" 한계를 수치로 제시 |
| FR-44 트렌드 | 직무별 공고 수 추이·키워드 빈도 |

### ⚠️ 확인 필요

- 명세의 출력 필드는 **목록(callTp=L)** 기준이다. 직무내용·자격요건·우대사항 같은 본문 필드가 없다.
  `title`만으로 기술을 추출하면 `JOB_REQUIRED_SKILL` 품질이 크게 떨어진다.
- **상세(callTp=D)** 응답에 본문이 포함되는지가 핵심이다. 상세 출력 명세는 `api_info/`에 없으므로
  인증키 발급 후 실제 호출로 먼저 확인할 것. 상세는 공고 1건당 1회 호출이라 호출 수 관리도 필요하다.
- 직종코드 목록(IT 계열)은 공통코드 API에서 받아야 한다.

---

## 2. 직무정보 — 표준직무기술서 (215L01)

수행직무 내용을 자유 텍스트로 넣으면 관련 NCS 능력단위와 지식·기술·태도를 돌려준다.

### 요청

`authKey`(필수), `jobCont`(필수, 수행직무내용), `limit`(선택, 기본 5건)

### 응답 (JSON, `result` → NCS 능력단위명)

| 필드 | 설명 |
| --- | --- |
| `job_sdvn` / `job_sdvn_cd` | NCS 능력단위명 / 세분류코드 |
| `ablt_def` | 능력단위 정의 |
| `ablt_unit` | 능력단위 코드 |
| `job_lcfn` / `job_lrcl_cd` | 대분류명 / 코드 |
| `job_mcn` / `job_mlsf_cd` | 중분류명 / 코드 |
| `job_scfn` / `job_scla_cd` | 소분류명 / 코드 |
| `knwg_tchn_attd` | 지식·기술·태도 |

실패 시: `message_cd`, `message`

### 활용처

- **FR-113 보완**: 공고 0건일 때 LLM 추정만 쓰는 대신 `knwg_tchn_attd`를 분리 → `SKILL` 임베딩 매칭 →
  공신력 있는 요구 기술 근거로 사용.
- **FR-33**: `ablt_def`를 로드맵 단계 "왜 이걸 해야 하는지" 설명의 재료로 LLM 프롬프트에 포함.
- NCS 대분류 `20`(정보통신)으로 IT 범위 필터링 가능.

### ⚠️ 팀 확인 필요 (스키마)

- `JOB_REQUIRED_SKILL.source` 값이 현재 `WORKNET / LLM / MANUAL`뿐이다. NCS 출처를 `WORKNET`으로
  묶을지, `NCS`를 추가할지 결정 필요.
- NCS 코드를 `JOB`에 저장하려면 컬럼 추가가 필요하다 → 스키마 변경이므로 먼저 논의.

---

## 3. 학과정보 (213L01) — 또래 비교 품질 확보

### 요청

`authKey`, `returnType=XML`, `target=MAJORCD`, `srchType`(`A` 전체 / `K` 키워드), `keyword` — 모두 필수

### 응답 (`<majorsList>` → `<majorList>`)

`total`, `majorGb`(1: 일반학과 / 2: 이색학과), `knowDtlSchDptNm`(세부학과명), `knowSchDptNm`(학과명),
`empCurtState1Id`(계열ID), `empCurtState2Id`(학과ID)

### 활용처

- `USERS.major`가 자유 입력이면 "컴퓨터공학과 / 컴공 / 컴퓨터공학부"가 서로 다른 값이 되어
  **FR-45 또래 비교(`SPEC_SCORE_HISTORY.major` 집계 키)가 쪼개진다.**
- 1주차 프로필 화면(FR-21)에 전공 자동완성으로 붙이고, 표준 학과명 문자열을 `major`에 저장한다.
  **스키마 변경 없음.**
- `srchType=A`로 전체 목록을 한 번 받아 캐시하면 매 입력마다 호출하지 않아도 된다.
- FR-38 직무 발굴의 "전공 역산" 입력값으로도 표준화된 전공명이 유리하다.

---

## 4. 직업정보 (212L01)

### 요청

`authKey`, `returnType=XML`, `target=JOBCD` (필수) / `srchType`(`K` 키워드, `C` 조건), `keyword`,
`avgSal`(10: 3천 미만 ~ 40: 5천 이상), `prospect`(1: 증가 ~ 5: 감소)

### 응답 (`<jobsList>` → `<jobList>`)

`total`, `jobClcd`(직업분류코드), `jobClcdNM`(직업분류명), `jobCd`(직업코드), `jobNm`(직업명)

### 활용처

- **1주차 `02_seed.sql`**: IT 직무 15~20개와 `JOB_ALIAS` 별칭을 만들 때 표준 직업명 참고용.
- 목록 API라 정보량이 적다. PDF 탭에 보이는 **"직업정보 상세"**가 하는 일·전망·임금을 준다면
  FR-35 `JOB_RECOMMENDATION.summary_json`(하는 일 / 필요 역량 / 전망)을 바로 채울 수 있다.
  상세 명세는 `api_info/`에 없음 → 추가 확보 필요.

---

## 5. 이번 범위에서 사용하지 않는 API

| API | 이유 |
| --- | --- |
| 강소기업 (216L01) | 우수기업 목록. 연결되는 FR 없음. 필요하면 채용정보의 `dtlSmlgntYn=Y` 필터로 충분 |
| 구직자취업역량 강화프로그램 (217L01) | 고용센터 오프라인 프로그램 일정. IT 로드맵과 무관하고, 쓰려면 새 테이블이 필요 (CLAUDE.md: 요청하지 않은 테이블 추가 금지) |

---

## 6. 빠져 있는 데이터 소스

| 필요한 것 | 이유 | 후보 |
| --- | --- | --- |
| 자격증 시험 일정 | `CERT_SCHEDULE`(FR-71 D-day 자동 생성)의 원천. 고용24 API에 없음 | 공공데이터포털 — 한국산업인력공단(Q-net) 국가자격 시험일정 API |
| 공통코드 API | 직종코드(`occupation`), 자격면허코드(`certLic`), 학과계열코드 조회 | 고용24 공통코드 API |
| 채용정보 상세 출력 명세 | 요구 기술 추출 품질을 좌우 | 고용24 명세 페이지 "채용정보상세" 탭 |
| 직업정보 상세 출력 명세 | FR-35 요약 채우기 | 고용24 명세 페이지 "직업정보 상세" 탭 |

---

## 7. 구현 메모

- **파싱**: 직무정보(JSON)를 제외하면 전부 XML. JDK 내장 DOM/StAX로 처리하면 의존성 추가가 없다.
- **인증키**: API별로 따로 신청하는 구조. `src/main/resources/.env`(`.gitignore` 등록됨)에 `WORK24_*_API_KEY`로
  분리해 넣고 `AppConfig.get(...)`으로 읽는다. 소스 하드코딩 금지(NFR-3).
- **캐시 키**: `EXTERNAL_API_CACHE`는 `(api_type, request_key)` 복합 UNIQUE이고 `api_type`이 전부 `WORKNET`이므로,
  `request_key`에 엔드포인트를 포함해 API끼리 충돌을 막는다. 예: `210L01:keyword=백엔드&career=N`
- **실패 처리 (FR-112)**: 호출 실패 시 캐시된 직전 결과로 대체, 없으면 "일시적으로 불러올 수 없음" 안내.
- **수집 주기 (TD-2 팀 확정)**: 채용정보 주 1회(일요일 새벽 배치), On-demand 캐시 TTL 7일,
  `JOB_SKILL_TREND` 스냅샷 매월 1일. 시계열은 소급할 수 없으므로 2주차 수집 시작과 동시에 쌓는다.
- **호출 위치**: 모든 외부 API 호출은 서버(service 계층)에서만. 클라이언트 JS 직접 호출 금지.
- **크롤링 금지**: 고용24는 공식 API가 있으므로 크롤링하지 않는다.
