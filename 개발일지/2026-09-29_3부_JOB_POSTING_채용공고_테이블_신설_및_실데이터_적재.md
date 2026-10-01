# JOB_POSTING(채용공고) 테이블 신설 및 실데이터 917건 적재

날짜: 2026-09-29
범위: FR-113(데이터 없는 직무 보완) — 최초 36개 테이블 스캐폴딩 이후 새로 확정된 담당 영역

## 1. 개요

명칭·요약·자격요건·우대사항·학력·급여·출처 같은 "개별 채용공고" 정보를 담을 테이블이 `docs/db-design.md` 36개 테이블 어디에도 없었다. 팀 확인 결과 이 담당이 비어 있었고(`.env`에 `WORK24_JOB_POSTING_API_KEY`만 미리 발급돼 있던 상태 — 누군가 고용24 채용정보 API 연동을 염두에 두긴 했던 것으로 보임), 로드맵 담당이 맡기로 확정해 새로 만들었다.

## 2. 스키마 설계 — 두 번 확정함

### 1차 설계 (7개 필드)

처음엔 "명칭/요약/자격요건/우대사항/학력/급여/출처" 요청 그대로 7개 컬럼으로 설계했다: `title`, `summary`, `qualifications`, `preferred`, `education_level`, `salary`, `source_url`.

### 2차 설계 — 실제 데이터 보고 재확정

실제 수집 대시보드 샘플(`docs/saved_resource.html` — 원티드·고용24 등 10개 출처, 1,606건 채용공고 집계 목록 화면)을 확인해보니 1차 설계로는 실제 데이터를 다 못 담는다는 게 드러났다:

- **회사명(`company_name`)**: 아예 빠져 있었음.
- **기술스택(`tech_stack`)**: 공고 하나에 여러 개가 붙는 다중값. 원래는 `JOB_REQUIRED_SKILL`처럼 SKILL과 N:M 연결 테이블로 빼는 게 정석이지만, 원문 기술명이 SKILL 마스터(163개)와 표기가 다르거나 아직 없는 경우가 많아서(예: Playwright, NestJS, Vite, gRPC) 무리하게 정규화하면 매칭 실패로 데이터가 누락된다. 콤마 구분 원문 텍스트로 우선 전부 보존하고, 매칭이 필요해지면 그때 연결 테이블로 승격하기로 함.
- **경력(`career_level`)**, **지역(`region`)**, **마감(`deadline`)**, **등록일(`posted_at`)**: 전부 빠져 있었음.
- **"출처"의 재정의**: 원래 출처를 URL 하나로 생각했는데, 실제론 **출처 시스템명**(원티드/고용24/CSI/CJK/CAT/CIN/KOS/MIT/PRD/CWK)과 **원문 링크**("보기" 버튼)가 별개였다. `source`(시스템명)와 `source_url`(원문 링크)로 분리.
- `deadline`을 DATE가 아니라 VARCHAR로 둔 이유: "상시"(마감 없음)가 매우 흔해서 DATE 컬럼으로는 이 값을 못 담는다.
- `salary`를 DECIMAL이 아니라 VARCHAR로 둔 이유: "회사내규에 따름", "연봉 2,600만원~2,800만원", "시급 10,320원~10,320원" 등 원문 표기가 텍스트로 오는 경우가 많아 숫자 하나로 정규화하면 정보 손실이 크다.

최종 컬럼: `job_id`(FK→JOB), `source`, `source_url`(UK), `title`, `company_name`, `summary`, `tech_stack`, `qualifications`, `preferred`, `career_level`, `education_level`, `salary`, `region`, `deadline`, `posted_at`, `collected_at` + 공통 컬럼.

스캐폴딩은 기존 `JOB_REQUIRED_SKILL` 패턴을 그대로 따랐다: `JobPostingDto`/`JobPostingDao`(읽기 + 등록만, `source_url` UNIQUE + `existsBySourceUrl()`로 재수집 중복 방지), `JobPostingDaoTest`.

## 3. 실데이터 917건 적재

`docs/saved_resource.html`은 눈에 보이는 목록 화면(50건, 페이지네이션 1/3)만 저장된 줄 알았는데, 다시 확인해보니 페이지 안 `<script>` 태그에 **`const RAW = [[...], ...]`** 형태로 **전체 1,606건 원본 데이터**가 `COLS` 17개 필드(`wanted_auth_no`, `info_type_cd`, `company`, `title`, `salary`, `career`, `education`, `region`, `close_date`, `close_type`, `reg_date`, `job_category`, `tech_stack`, `requirements`, `preferred`, `detail_url`, `keywords`)와 함께 통째로 숨어 있었다("보기" 버튼을 누르면 이 데이터를 화면에 펼쳐 보여주는 방식). 대시보드에 찍힌 "1,606건"이 실제로 다 들어있었던 것.

- **직무 분류**: 이 프로젝트는 "IT 계열 직무 한정"이 스코프인데, 원본 1,606건에는 생산·기계·전자·로봇공학·전기계측 같은 비-IT 직군도 섞여 있었다(원 수집 키워드가 "개발" 전반을 넓게 훑은 결과로 보임). WorkNet 공식 직종 분류(`job_category`) → 제목 키워드 → 기술스택 다수결 순으로 분류해서, 우리 JOB 18개 중 하나에 명확히 대응되는 **917건만** 적재하고 나머지 **689건**(비-IT 직군 144건, 임베디드/펌웨어 36건, 신호 부족 506건 등)은 스코프 밖으로 제외했다.
- 최종 분포: 백엔드 개발자 503 · 프론트엔드 개발자 188 · DevOps 엔지니어 89 · 데이터 엔지니어 43 · 보안 엔지니어 36 · 시스템 엔지니어 25 · 서비스 기획자 15 · 클라우드 엔지니어 6 · UI 개발자 6 · 데이터 분석가 4 · 데이터 사이언티스트 2.
- `summary`/`qualifications`/`preferred` 중 `summary`는 이번 데이터에 없었다(원본이 "담당업무"와 "자격요건"을 `requirements` 한 필드에 합쳐서 제공 — 정보 손실 없이 전부 `qualifications`에 그대로 보존).
- 이 자동 분류는 100% 정확하다고 보장 못 한다("풀스택", "Head of Engineering"처럼 애매한 제목은 기술스택 다수결로 임의 배정) — 나중에 필요하면 LLM 분류로 다시 다듬을 수 있음.
- 공유 원격 DB에 직접 적재(파이썬 스크립트로 파싱 + pymysql 삽입, 일회성 작업이라 별도 커밋은 없음).

## 4. 관련 커밋

```
2045623 feat: JOB_POSTING(채용공고) 테이블 신설 — 스캐폴딩
dba10d4 fix: JOB_POSTING 컬럼 재설계 — 실제 수집 데이터 기준 재확인
```
