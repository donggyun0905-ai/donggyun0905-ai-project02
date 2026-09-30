# -*- coding: utf-8 -*-
"""고용24 채용정보 크롤러 (프로그래머 관련 공고) — 결과를 TSV 형식 txt로 저장.

1. 목록: 여러 키워드로 검색 후 (공고번호, 정보유형) 기준으로 중복 제거
2. 필터: 제목에 개발 관련 단어가 없는 공고는 제외
3. 상세: 공고별 상세 페이지에서 요구사항(직무내용·자격요건·우대사항)을 가져오고
         sql/04_seed_skills.sql 의 SKILL 표준 명칭으로 기술스택을 추출

- 요청 간격 1.5초, robots.txt 허용 경로(/wk/)만 사용.
- 상세 페이지는 crawl/.cache/details.jsonl 에 캐시 → 중간에 끊겨도 다시 실행하면 이어서 받는다.
- 민간연계 공고(원티드·사람인 등)는 고용24 페이지에 있는 내용까지만 수집한다 (원본 사이트는 따라가지 않음).

사용: python crawl/work24_crawler.py [출력파일]
"""
import html
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import date

LIST_URL = "https://www.work24.go.kr/wk/a/b/1200/retriveDtlEmpSrchList.do"
BASE_URL = "https://www.work24.go.kr"
UA = "Mozilla/5.0 (SpecOdyssey study project crawler)"
DELAY_SEC = 1.5
PAGE_SIZE = 100
MAX_PAGES_PER_KEYWORD = 30

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_PATH = os.path.join(HERE, ".cache", "details.jsonl")
SKILL_SEED_PATH = os.path.join(HERE, "..", "sql", "04_seed_skills.sql")

# 너무 넓은 키워드(소프트웨어, SW개발, 데이터엔지니어)는 비개발 공고가 섞여 제외
KEYWORDS = ["개발자", "프로그래머", "백엔드", "프론트엔드", "풀스택", "웹개발", "앱개발", "DevOps"]

# 본문 검색이라 개발과 무관한 공고도 걸린다 → 제목에 이 단어가 없으면 제외
DEV_TITLE_RE = re.compile(
    r"개발|develop|engineer|엔지니어|프로그래|backend|frontend|백엔드|프론트|풀스택|devops|앱|웹|sw|software|"
    r"소프트웨어|java|python|react|서버|데이터|ai|it|전산|시스템", re.I)

# 검색 폼(mForm) 기본값 — 사이트가 이 필드들이 있어야 검색을 정상 처리한다
FORM_DEFAULTS = {
    "currentPageNo": "1", "pageIndex": "1", "resultCnt": str(PAGE_SIZE),
    "sortOrderBy": "DESC", "sortField": "DATE",
    "keywordWantedTitle": "N", "keywordBusiNm": "N", "keywordJobCont": "N", "keywordStaAreaNm": "N",
    "siteClcd": "all", "codeDepth1Info": "11000", "codeDepth2Info": "11000",
    "benefitSrchAndOr": "O", "empTpGbcd": "1", "essCertChk": "N",
}

COLUMNS = ["wanted_auth_no", "info_type_cd", "company", "title", "salary", "career", "education",
           "region", "close_date", "close_type", "reg_date", "job_category", "tech_stack",
           "requirements", "preferred", "detail_url", "keywords"]

# SKILL 표준 명칭별 추가 표기 (한글·약어). 기본 표기(명칭 그대로)는 자동으로 포함된다.
SKILL_ALIASES = {
    "Java": [r"자바(?!\s*스크립트)"],
    "JavaScript": [r"자바\s*스크립트", r"(?<![A-Za-z])JS(?![A-Za-z])", r"ES6"],
    "TypeScript": [r"타입\s*스크립트"],
    "Python": [r"파이썬"],
    "Kotlin": [r"코틀린"], "Swift": [r"스위프트"], "Dart": [r"다트"],
    "C++": [r"(?<![A-Za-z])C\s*\+\+", r"Cpp"],
    "C": [r"C\s*언어", r"(?<![A-Za-z])C\s*/\s*C\s*\+\+"],
    "R": [r"R\s*언어", r"RStudio"],
    "Go": [r"Golang", r"Go\s*언어"],
    "Shell Script": [r"쉘\s*스크립트", r"셸\s*스크립트", r"Shell"],
    "Spring": [r"스프링(?!\s*부트)"], "Spring Boot": [r"SpringBoot", r"스프링\s*부트"],
    "Django": [r"장고"], "Node.js": [r"NodeJS", r"Node(?![A-Za-z.])", r"노드\s*JS"],
    "Express.js": [r"Express"], "NestJS": [r"Nest\.js"],
    ".NET": [r"닷넷"], "ASP.NET Core": [r"ASP\.NET"],
    "React": [r"리액트", r"React\.js", r"ReactJS"], "Vue.js": [r"Vue", r"뷰\.js"],
    "Next.js": [r"NextJS"], "Nuxt.js": [r"Nuxt"],
    "HTML5": [r"HTML"], "CSS3": [r"CSS"], "Tailwind CSS": [r"Tailwind"],
    "Android SDK": [r"Android", r"안드로이드"], "Flutter": [r"플러터"],
    "Oracle Database": [r"Oracle", r"오라클"], "MS SQL Server": [r"MSSQL", r"MS-SQL", r"SQL\s*Server"],
    "PostgreSQL": [r"Postgres"], "MongoDB": [r"Mongo"],
    "Amazon RDS": [r"RDS"], "AWS": [r"Amazon Web Services", r"아마존\s*웹"],
    "Google Cloud Platform": [r"GCP"], "Microsoft Azure": [r"Azure", r"애저"],
    "Docker": [r"도커"], "Kubernetes": [r"k8s", r"쿠버네티스"],
    "GitLab CI/CD": [r"GitLab\s*CI"], "ELK Stack": [r"ELK"], "Elasticsearch": [r"Elastic\s*Search", r"엘라스틱"],
    "Load Balancing": [r"로드\s*밸런", r"Load\s*Balanc"],
    "Apache Spark": [r"Spark"], "Apache Kafka": [r"Kafka", r"카프카"], "Apache Airflow": [r"Airflow"],
    "Apache Flink": [r"Flink"], "Apache NiFi": [r"NiFi"], "Apache JMeter": [r"JMeter"],
    "Google BigQuery": [r"BigQuery"], "Amazon Redshift": [r"Redshift"],
    "Hugging Face Transformers": [r"Hugging\s*Face"], "Jupyter Notebook": [r"Jupyter"],
    "scikit-learn": [r"sklearn"], "Pandas": [r"판다스"],
    "Linux": [r"리눅스"], "Windows Server": [r"윈도우\s*서버"],
    "Git": [r"깃(?![A-Za-z가-힣])"], "Jira": [r"지라"],
    "REST API": [r"RESTful", r"REST(?![A-Za-z])"],
    "Microservices Architecture": [r"MSA", r"마이크로\s*서비스", r"Microservice"],
    "Design Patterns": [r"디자인\s*패턴", r"Design\s*Pattern"],
    "Agile/Scrum": [r"Agile", r"Scrum", r"애자일", r"스크럼"],
    "Unit Testing": [r"단위\s*테스트", r"Unit\s*Test"],
    "UI/UX Design": [r"UI\s*/\s*UX", r"UX\s*/\s*UI"], "Figma": [r"피그마"],
    "TCP/IP": [r"TCP"], "OAuth 2.0": [r"OAuth"],
}
# 한두 글자라 기본 표기로 찾으면 오탐이 많은 명칭 → 별칭으로만 찾는다
ALIAS_ONLY = {"C", "R"}
CASE_SENSITIVE = {"Go"}  # 영어 문장의 go 와 구분


def load_skill_patterns():
    s = open(SKILL_SEED_PATH, encoding="utf-8").read()
    block = s.split("INSERT INTO SKILL", 1)[1].split(";", 1)[0]
    names = [n.replace("''", "'") for n, _ in re.findall(r"\('((?:[^']|'')*)',\s*'([^']*)'\)", block)]
    patterns = []
    for name in names:
        alts = list(SKILL_ALIASES.get(name, []))
        if name not in ALIAS_ONLY:
            alts.insert(0, re.escape(name))
        flags = 0 if name in CASE_SENSITIVE else re.I
        # 영문·숫자·기호(+#.)가 앞뒤에 붙어 있으면 다른 단어의 일부로 본다 (Java ≠ JavaScript, SQL ≠ MySQL)
        rx = r"(?<![A-Za-z0-9+#.])(?:" + "|".join(alts) + r")(?![A-Za-z0-9+#])"
        patterns.append((name, re.compile(rx, flags)))
    return patterns


def http_get(url, data=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as res:
        return res.read().decode("utf-8", "replace")


def text(fragment):
    t = html.unescape(re.sub(r"<[^>]+>", " ", fragment))
    return re.sub(r"\s+", " ", t).strip()


# ---------------------------------------------------------------- 목록

def fetch_list(keyword, page):
    form = dict(FORM_DEFAULTS, keyword=keyword, srcKeyword=keyword,
                currentPageNo=str(page), pageIndex=str(page))
    return http_get(LIST_URL, urllib.parse.urlencode(form).encode())


def parse_rows(page_html):
    rows = []
    for chunk in re.split(r'<tr id="list\d+"', page_html)[1:]:
        chunk = chunk.split("</tr>", 1)[0]
        cb = re.search(r'id="chkboxWantedAuthNo\d+" value="([^"]*)"', chunk)
        if not cb:
            continue
        parts = html.unescape(cb.group(1)).split("|")
        if len(parts) < 4:
            continue
        auth_no, info_type, company, title = parts[0], parts[1], parts[2], "|".join(parts[3:])
        href = re.search(r'href="(/wk/a/b/1500/empDetailAuthView\.do\?[^"]+)"', chunk)
        dollar = re.search(r'<li class="dollar">(.*?)</li>', chunk, re.S)
        member = re.search(r'<li class="member">(.*?)</li>', chunk, re.S)
        site = re.search(r'<li class="site">(.*?)</li>', chunk, re.S)
        member_items = re.findall(r'<span class="item[^"]*">(.*?)</span>', member.group(1), re.S) if member else []
        close_dt = re.search(r"var date\s*=\s*'([^']*)'", chunk)
        close_tp = re.search(r"var closeTpNm\s*=\s*'([^']*)'", chunk)
        reg_dt = re.search(r"등록일\s*:\s*([\d-]+)", chunk)
        close = close_dt.group(1) if close_dt else ""
        rows.append({
            "wanted_auth_no": auth_no,
            "info_type_cd": info_type,
            "company": company.strip(),
            "title": title.strip(),
            "salary": text(dollar.group(1)) if dollar else "",
            "career": text(member_items[0]) if len(member_items) > 0 else "",
            "education": text(member_items[1]) if len(member_items) > 1 else "",
            "region": text(site.group(1)) if site else "",
            "close_date": "" if close.startswith("2099") else close,  # 2099-12-31 = 채용시까지
            "close_type": close_tp.group(1) if close_tp else "",
            "reg_date": reg_dt.group(1) if reg_dt else "",
            "detail_url": BASE_URL + html.unescape(href.group(1)) if href else "",
        })
    return rows


def total_count(page_html):
    m = re.search(r"검색건수\s*([\d,]+)", text(page_html))
    return int(m.group(1).replace(",", "")) if m else 0


def crawl_list():
    jobs = {}  # (wanted_auth_no, info_type_cd) -> row (키워드는 누적)
    for kw in KEYWORDS:
        page, total = 1, None
        while page <= MAX_PAGES_PER_KEYWORD:
            try:
                page_html = fetch_list(kw, page)
            except Exception as e:  # 한 페이지 실패로 전체를 멈추지 않는다
                print(f"[WARN] {kw} p{page} 실패: {e}", file=sys.stderr)
                break
            if total is None:
                total = total_count(page_html)
                print(f"[목록] {kw}: 검색건수 {total}", flush=True)
            rows = parse_rows(page_html)
            for r in rows:
                key = (r["wanted_auth_no"], r["info_type_cd"])  # 연계 사이트 번호끼리 겹칠 수 있음
                if key in jobs:
                    if kw not in jobs[key]["keywords"]:
                        jobs[key]["keywords"].append(kw)
                else:
                    r["keywords"] = [kw]
                    jobs[key] = r
            time.sleep(DELAY_SEC)
            # 사이트가 한 페이지에 100건보다 적게 줄 때가 있어 건수 부족만으로는 끝으로 보지 않는다
            if not rows or page * PAGE_SIZE >= total:
                break
            page += 1
    return jobs


# ---------------------------------------------------------------- 상세

# 상세 페이지 탭 제목. 본문도 같은 제목으로 구역이 나뉜다.
SECTION_TITLES = {"모집요강", "근무조건", "우대사항", "기타사항", "복리후생", "작업환경", "전형방법", "기업정보", "추천정보"}
NOISE_LINES = {"더보기", "접기", "도움말", "닫기"}  # "-"는 표의 빈 값이라 남겨둔다 (항목-값 짝 유지)


def page_lines(page_html):
    s = re.sub(r"<script.*?</script>|<style.*?</style>|<!--.*?-->", "", page_html, flags=re.S)
    s = re.sub(r"<br\s*/?>|</(p|div|li|tr|dd|dt|th|td|h\d)>", "\n", s, flags=re.I)
    t = html.unescape(re.sub(r"<[^>]+>", "\n", s))
    lines = [re.sub(r"\s+", " ", l).strip() for l in t.split("\n")]
    return [l for l in lines if l]


def split_sections(lines):
    """탭 목록(모집요강·근무조건·… 연속 나열)을 건너뛰고 본문을 구역별로 나눈다."""
    start = None
    for i in range(len(lines) - 1):
        if lines[i] == "모집요강" and lines[i + 1] == "근무조건":
            j = i
            while j < len(lines) and lines[j] in SECTION_TITLES:
                j += 1
            start = j
            break
    if start is None:
        return {}
    tabs = set(lines[i:start])  # 이 공고 페이지에 실제로 있는 탭 (민간연계는 우대사항 탭이 없다)
    sections, cur = {}, "모집요강"
    for l in lines[start:]:
        if l in tabs and l != cur and l not in sections:
            cur = l
            continue
        if l.endswith("제공하는 표") or l in NOISE_LINES:
            continue
        sections.setdefault(cur, []).append(l)
    return sections


def value_after(lines, label, stop_labels):
    """'모집 직종' 같은 표 항목의 값(다음 항목 이름 전까지)을 가져온다."""
    if label not in lines:
        return ""
    i = lines.index(label) + 1
    vals = []
    while i < len(lines) and lines[i] not in stop_labels:
        vals.append(lines[i])
        i += 1
    return " ".join(v for v in vals if v not in {",", "-"}).strip()


TABLE_LABELS = {"모집 인원", "장애인 채용 인원", "모집 직종", "관련 직종", "직종 키워드", "경력", "학력",
                "자격 면허", "고용형태", "근무 예정지", "임금조건", "채용 직급", "우대조건"}
PREF_LABELS = ["전공", "컴퓨터 활용 능력", "외국어 능력", "우대조건", "기타 우대사항"]
PREF_STOPS = set(PREF_LABELS) | {"기타사항", "고용허가제", "병역 대체 복무자 채용", "그 밖의 희망사항"}


def parse_detail(page_html):
    sections = split_sections(page_lines(page_html))
    recruit = sections.get("모집요강", [])
    job_category = value_after(recruit, "모집 직종", TABLE_LABELS)
    job_keywords = value_after(recruit, "직종 키워드", TABLE_LABELS)
    license_ = value_after(recruit, "자격 면허", TABLE_LABELS)
    pref_cond = value_after(recruit, "우대조건", TABLE_LABELS)

    # 직무내용 본문: 고용24 공고는 표(모집 인원…) 앞까지, 민간연계는 모집요강 전체
    body = recruit
    if "직무내용" in body:
        body = body[body.index("직무내용") + 1:]
    for stop in ("모집 인원", "모집 직종"):
        if stop in body:
            body = body[:body.index(stop)]
    requirements = " / ".join(l for l in body if l != "-")
    extra = [f"직종 키워드: {job_keywords}" if job_keywords else "",
             f"자격 면허: {license_}" if license_ and license_ != "관계없음" else ""]
    requirements = " / ".join(x for x in [requirements] + extra if x)

    pref_section = sections.get("우대사항", [])
    preferred = " / ".join(
        f"{label}: {v}" for label in PREF_LABELS if (v := value_after(pref_section, label, PREF_STOPS)))
    if pref_cond:
        preferred = " / ".join(x for x in [f"우대조건: {pref_cond}", preferred] if x)
    return {"job_category": job_category, "requirements": requirements, "preferred": preferred}


def load_cache():
    cache = {}
    if os.path.exists(CACHE_PATH):
        with open(CACHE_PATH, encoding="utf-8") as f:
            for line in f:
                try:
                    d = json.loads(line)
                    cache[(d["wanted_auth_no"], d["info_type_cd"])] = d
                except (ValueError, KeyError):
                    continue  # 중간에 끊겨 반쯤 쓰인 줄
    return cache


def crawl_details(jobs):
    os.makedirs(os.path.dirname(CACHE_PATH), exist_ok=True)
    cache = load_cache()
    todo = [k for k in jobs if k not in cache]
    print(f"[상세] 대상 {len(jobs)}건 · 캐시 {len(jobs) - len(todo)}건 · 새로 받을 {len(todo)}건 "
          f"(약 {len(todo) * (DELAY_SEC + 0.5) / 60:.0f}분)", flush=True)
    with open(CACHE_PATH, "a", encoding="utf-8") as out:
        for n, key in enumerate(todo, 1):
            try:
                d = parse_detail(http_get(jobs[key]["detail_url"]))
            except Exception as e:
                print(f"[WARN] 상세 실패 {key}: {e}", file=sys.stderr)
                d = None
            if d is not None:
                d.update(wanted_auth_no=key[0], info_type_cd=key[1])
                cache[key] = d
                out.write(json.dumps(d, ensure_ascii=False) + "\n")
                out.flush()
            if n % 50 == 0 or n == len(todo):
                print(f"  {n}/{len(todo)}", flush=True)
            time.sleep(DELAY_SEC)
    return cache


# ---------------------------------------------------------------- 저장

def clean(v):
    return re.sub(r"[\t\r\n]+", " ", str(v))


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, f"work24_dev_jobs_{date.today():%Y%m%d}.txt")
    skills = load_skill_patterns()

    jobs = crawl_list()
    before = len(jobs)
    jobs = {k: r for k, r in jobs.items() if DEV_TITLE_RE.search(r["title"])}
    print(f"[필터] 고유 공고 {before}건 → 제목 기준 개발 공고 {len(jobs)}건", flush=True)

    details = crawl_details(jobs)

    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\t".join(COLUMNS) + "\n")
        for key, r in jobs.items():
            d = details.get(key, {"job_category": "", "requirements": "", "preferred": ""})
            haystack = " ".join([r["title"], d["job_category"], d["requirements"], d["preferred"]])
            tech = [name for name, rx in skills if rx.search(haystack)]
            row = dict(r, **d, tech_stack=",".join(tech), keywords=",".join(r["keywords"]))
            f.write("\t".join(clean(row[c]) for c in COLUMNS) + "\n")
    print(f"완료: {len(jobs)}건 -> {out_path}")


if __name__ == "__main__":
    main()
