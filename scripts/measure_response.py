"""화면 응답 시간 측정 (docs/performance.md의 수치를 낸 스크립트, 2026-10-10)

사용: python scripts/measure_response.py <기준URL> <아이디> <비밀번호> [반복 횟수=30]
  예) python scripts/measure_response.py http://localhost:8090 perf_user 'Test1234!' 30

로그인한 뒤 대시보드(/dashboard)와 로드맵(/roadmap)을 번갈아 요청하고, 화면마다 중앙값·p90·평균(ms)을 출력한다.
처음 3번은 워밍업(JSP 컴파일·캐시)이라 집계에서 뺀다. 표준 라이브러리만 쓴다.
"""
import http.cookiejar
import re
import statistics
import sys
import time
import urllib.parse
import urllib.request

PAGES = ["/dashboard", "/roadmap"]
WARMUP = 3


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    base, login_id, password = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]
    repeat = int(sys.argv[4]) if len(sys.argv) > 4 else 30

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    login_page = opener.open(base + "/login").read().decode("utf-8")
    csrf = re.search(r'name="_csrf" value="([^"]+)"', login_page).group(1)
    body = urllib.parse.urlencode({"_csrf": csrf, "loginId": login_id, "password": password}).encode()
    landed = opener.open(base + "/login", body).geturl()
    if landed.rstrip("/").endswith("/login"):
        sys.exit("로그인 실패 — 아이디·비밀번호를 확인하세요")

    samples = {page: [] for page in PAGES}
    for i in range(WARMUP + repeat):
        for page in PAGES:
            start = time.perf_counter()
            with opener.open(base + page) as res:
                res.read()
                if res.status != 200 or res.geturl().rstrip("/").endswith("/login"):
                    sys.exit(f"{page} 응답 이상: {res.status} {res.geturl()}")
            elapsed = (time.perf_counter() - start) * 1000
            if i >= WARMUP:
                samples[page].append(elapsed)

    print(f"반복 {repeat}회 (워밍업 {WARMUP}회 제외)")
    for page, values in samples.items():
        values.sort()
        p90 = values[max(0, int(len(values) * 0.9) - 1)]
        print(f"{page:12s} 중앙값 {statistics.median(values):7.1f} ms   p90 {p90:7.1f} ms   평균 {statistics.mean(values):7.1f} ms")


if __name__ == "__main__":
    main()
