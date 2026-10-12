# 클라우드플레어 터널로 배포하기

> 내 PC에서 돌고 있는 톰캣을 공개 주소(`https://…`)로 띄운다.
> 공인 IP도, 공유기 포트 포워딩도, 인증서 발급도 필요 없다.
> 준비 기준: 2026-10-12 · 테스트 1,066개 통과

---

## 어떻게 동작하나

```
  면접관 브라우저
        │  https://spec.example.com
        ▼
  클라우드플레어 가장자리 서버
        │  (터널 — 바깥에서 들어오는 연결이 아니다)
        ▼
  내 PC 의 cloudflared  ──►  http://localhost:8080  (톰캣)
```

핵심은 **cloudflared 가 밖으로 연결을 거는 것**이라는 점이다. 바깥에서 내 PC로 들어오는
포트가 하나도 열리지 않으므로 공유기 설정을 건드리지 않고, 방화벽 예외도 필요 없다.
https 인증서는 클라우드플레어가 알아서 붙인다.

대신 **cloudflared 와 톰캣 사이는 평문 http** 다. 이게 아래 "걸리는 것들"의 출발점이다.

---

## 10/11 에 한 번 띄워 봤다면 — 달라진 것

[`개발일지/2026-10-11_2부_시연용_Cloudflare터널_공개주소.md`](../개발일지/2026-10-11_2부_시연용_Cloudflare터널_공개주소.md)
에서 임시 터널로 이미 확인을 마쳤다. 그때와 비교해 **PC 마다 손으로 하던 일 하나가 없어졌다.**

| | 10/11 | 지금 |
| --- | --- | --- |
| RemoteIpValve | 톰캣 `conf\server.xml` 을 직접 고치고 백업 (PC 마다 반복) | **WAR 안 `META-INF/context.xml` 에 들어 있다 — 할 일 없음** |
| 주소 | `cloudflared tunnel --url` → 켤 때마다 바뀌는 `trycloudflare.com` | 임시 주소도 그대로 쓸 수 있고, 고정 주소도 고를 수 있다 |
| 올리기 전 확인 | 눈으로 | `deploy\start-tunnel.ps1` 이 막히는 것들을 먼저 잡는다 |

**집 PC 의 `server.xml` 에 넣어 둔 Valve 는 지워도 되고 둬도 된다.** 둘 다 있어도 문제가
없다 — 앞선 밸브가 이미 접속자 IP 를 진짜 IP 로 바꿔 놓기 때문에, 뒤의 밸브는 그 IP 가
`internalProxies` 에 없는 걸 보고 아무 일도 하지 않는다. 헷갈리지 않게 지우는 쪽을 권한다
(백업해 둔 `server.xml.bak-20261011` 로 되돌리면 된다).

---

## 어느 쪽으로 띄울까

| | 임시 터널 | 고정 터널 |
| --- | --- | --- |
| 명령 | `cloudflared tunnel --url http://localhost:8080` | `cloudflared tunnel run spec-odyssey` |
| 주소 | `https://○○○.trycloudflare.com` — **켤 때마다 바뀐다** | 내 도메인 — 안 바뀐다 |
| 준비 | 없음. 로그인도 필요 없다 | 도메인 + 로그인 + 터널 생성 (한 번만) |
| 맞는 때 | **시연 당일 한 번 띄우는 용도** | 발표 뒤에도 링크를 살려 둘 때 |

시연만 할 거면 임시 터널로 충분하다. 다만 **주소가 바뀌면 이미 발급한 공유 링크도 못 쓴다** —
링크는 "지금 접속한 주소" 기준으로 만들어지기 때문이다. 시연 직전에 켠 주소로 다시 발급하면 된다.

---

## 준비물

| | |
| --- | --- |
| 도메인 | 클라우드플레어에 네임서버가 연결된 도메인 하나 |
| cloudflared | `winget install --id Cloudflare.cloudflared` |
| 톰캣 | 10.1 이상, WAR 가 올라가 있고 `http://localhost:8080` 에서 응답 |
| `.env` | 배포할 PC 의 `src/main/resources/.env` 에 DB 접속 정보와 API 키 |

---

## 코드 쪽은 이미 준비돼 있다

따로 고칠 것이 없다. 무엇이 왜 들어갔는지만 알아 두면 된다.

### `META-INF/context.xml` — RemoteIpValve

WAR 안에 들어가므로 팀원마다 `server.xml` 을 고칠 필요가 없다.

cloudflared 는 톰캣에 **평문 http** 로 붙는다. 그래서 그냥 두면 톰캣이
"이 요청은 `http://<호스트>:8080` 으로 들어왔다"고 생각한다. 그러면:

- **공유 링크가 밖에서 안 열린다** — `http://내도메인:8080/share/…` 로 만들어진다
- 캐릭터에 넘기는 `server=` 주소도 같은 문제를 겪는다
- `request.isSecure()` 가 false 라 **세션 쿠키에 Secure 표시가 안 붙는다**
- 접속자 IP 가 전부 `127.0.0.1` 로 보여 **감사 로그가 쓸모없어진다**

밸브가 cloudflared 가 붙여 주는 `X-Forwarded-Proto` / `X-Forwarded-For` 를 읽어
scheme·port·remoteAddr 를 바로잡는다. 그 뒤로는 애플리케이션 코드가 평소처럼
`req.getScheme()` 만 보면 된다 — 프록시를 아는 코드를 따로 두지 않았다.

`internalProxies` 는 루프백만 믿도록 좁혀 뒀다. 아무 데서나 보낸 `X-Forwarded-For` 를
믿으면 **접속자 IP 를 마음대로 속일 수 있다.** 다른 PC 에서 프록시를 돌릴 게 아니라면 넓히지 말 것.

### `PublicUrlBehindProxyTest`

밖으로 내보내는 절대 주소(공유 링크, 캐릭터 `server=`)가 `localhost:8080` 으로 새지 않는지
빌드에서 잡는다. 터널 뒤(https·443)에서는 포트가 붙지 않고, 로컬 개발(http·8080)에서는
예전 그대로여야 한다 — 둘 다 고정해 뒀다.

### 세션 쿠키

따로 설정하지 않았다. 톰캣은 **요청이 보안 연결이면 세션 쿠키에 Secure 를 자동으로 붙인다.**
밸브가 `isSecure()` 를 바로잡아 주므로, 터널 뒤에서는 Secure 가 붙고 로컬 http 에서는 안 붙는다.
`web.xml` 에 `<secure>true</secure>` 를 박으면 **로컬 개발에서 로그인이 안 된다** — 그래서 안 넣었다.
`SameSite=Lax` 는 `META-INF/context.xml` 에 이미 있다.

---

## 띄우는 순서 ① 임시 터널 — 시연 당일

```powershell
# 1) 설치 (한 번만)
winget install --id Cloudflare.cloudflared

# 2) IntelliJ 톰캣이라면 Application context 를 "/" 로 바꾼다
#    Run > Edit Configurations > Tomcat > Deployment 탭
#    안 바꾸면 주소 뒤에 /spec_odyssey_war_exploded/ 를 붙여야 열린다 (그냥 들어가면 404)

# 3) 톰캣 실행 — WAR 를 새로 올려야 context.xml 의 Valve 가 반영된다

# 4) 절전 모드 끄기 (PC 가 자면 터널도 끊긴다)
powercfg /change standby-timeout-ac 0

# 5) 터널 — 창을 계속 열어 둔다
cloudflared tunnel --url http://localhost:8080
#    출력 표 안의 https://○○○.trycloudflare.com 주소로 접속한다
```

확인부터 하고 띄우려면 `.\deploy\start-tunnel.ps1 -Quick` 을 쓴다 — 톰캣이 떠 있는지,
WAR 에 Valve 가 들어갔는지 먼저 보고 임시 터널을 올린다.

---

## 띄우는 순서 ② 고정 터널 — 주소를 계속 쓸 때

```powershell
# 1) 클라우드플레어 계정에 로그인 (브라우저가 열린다)
cloudflared tunnel login

# 2) 터널 만들기 — 터널 ID 와 자격 증명 파일 경로를 알려 준다
cloudflared tunnel create spec-odyssey

# 3) 도메인을 이 터널로 연결
cloudflared tunnel route dns spec-odyssey spec.example.com

# 4) 설정 파일 복사해서 값 채우기
copy deploy\cloudflared\config.example.yml $env:USERPROFILE\.cloudflared\config.yml
notepad $env:USERPROFILE\.cloudflared\config.yml
#    → tunnel, credentials-file, hostname 세 군데를 바꾼다

# 5) 띄우기 (먼저 막히는 것들을 확인하고 올린다)
.\deploy\start-tunnel.ps1
```

`start-tunnel.ps1` 은 올리기 전에 cloudflared 설치 · 설정 파일 · `httpHostHeader` 유무 ·
톰캣 응답 · RemoteIpValve 유무를 확인한다. **터널은 떴는데 사이트가 안 열리는 상황**을
만들지 않는 게 목적이다. 하나라도 어긋나면 올리지 않고 무엇이 문제인지 알려 준다.

### 창을 닫아도 유지하려면

위 명령은 창을 닫으면 끊긴다. 계속 띄워 두려면 윈도우 서비스로 등록한다.

```powershell
cloudflared service install      # 관리자 권한 PowerShell
```

---

## 걸리는 것들 — 미리 알고 가야 하는 것

### 1. `httpHostHeader` 를 설정하면 모든 POST 가 403 이 된다

> 고정 터널(`config.yml`)에만 해당한다. 임시 터널(`--url`)은 설정 파일이 없어 이 문제가 없다.

가장 많이 걸리는 지점이다. `config.yml` 에 이렇게 쓰고 싶어진다:

```yaml
originRequest:
  httpHostHeader: localhost:8080   # ← 절대 쓰지 말 것
```

이러면 톰캣이 받는 `Host` 헤더가 `localhost:8080` 이 된다. 그런데 브라우저가 보내는
`Origin` 은 `https://spec.example.com` 이다. `SecurityHeadersFilter` 는 이 둘의 호스트가
같아야 상태 변경 요청을 통과시키므로(CSRF 2차 방어), **로그인부터 모든 POST 가 403** 이 된다.

cloudflared 는 기본적으로 원래 Host 를 그대로 넘긴다. 아무것도 쓰지 않는 게 맞다.
`start-tunnel.ps1` 이 이 줄이 있으면 올리기 전에 막는다.

### 2. 요청 본문 100MB 한도 — 캐릭터 설치 파일 업로드가 막힌다

클라우드플레어 무료 플랜은 **요청 본문을 100MB 로 자른다.** 우리 쪽 한도는 이렇다.

| 올리는 것 | 코드상 한도 | 터널 통과 |
| --- | --- | --- |
| 서류·이력서·공부노트 PDF | 20MB | 통과 |
| 프로젝트 서류 묶음 | 요청 전체 100MB | 아슬아슬 — 90MB 넘기지 말 것 |
| **캐릭터 설치 파일(exe)** | **200MB** | **100MB 넘으면 413** |

설치 파일이 100MB 를 넘으면 **터널 말고 `http://localhost:8080/admin/companion` 으로
직접 올린다.** 톰캣이 도는 그 PC 에서 하면 된다. 관리자만 쓰는 기능이라 이 방법으로 충분하고,
이것 때문에 코드 한도를 낮추지는 않았다 — 로컬에서는 200MB 가 멀쩡히 동작한다.

### 3. 100초 넘게 걸리는 화면은 524 가 뜬다

클라우드플레어 가장자리가 **100초에서 응답을 포기하고 524** 를 돌려준다.
LLM 을 기다리는 화면(격차 분석, 로드맵 생성, 자소서 첨삭)이 여기에 가까워질 수 있다.

우리 쪽 외부 호출 타임아웃은 LLM 60초 · 그 외 10~20초다. 재시도(429·5xx)가 겹치면
한 요청이 100초를 넘길 수 있다. 그래서 `config.yml` 의 `connectTimeout` 을 30초로 잡아
**끊길 때 클라우드플레어의 524 대신 우리 오류 화면이 먼저 나오게** 했다.

발표 전에 격차 분석을 한 번 끝까지 돌려 보고 몇 초 걸리는지 재 두는 게 좋다.

### 4. 정적 파일이 캐시된다 — 배포해도 화면이 안 바뀐다

> 내 도메인으로 띄울 때만 해당한다. 임시 터널(`trycloudflare.com`)은 캐시를 타지 않는다.

클라우드플레어는 `/css/`, `/js/`, `/image/` 같은 정적 파일을 가장자리에 캐시한다.
WAR 를 새로 올려도 **옛날 CSS·JS 가 계속 나갈 수 있다.**

새로 배포한 뒤 화면이 그대로면 대시보드에서 **Caching → Purge Everything** 을 한 번 누른다.
발표 직전에 배포했는데 화면이 예전 모습이면 십중팔구 이것이다.

HTML 은 기본적으로 캐시되지 않으므로 로그인 상태가 섞이는 일은 없다.

### 5. 공유 DB 에 스케줄러가 겹친다

`@WebListener` 로 도는 것들(점수 스냅샷 · 알림 · 트렌드 수집 · 워크넷 수집 · 탈퇴 유예 정리)은
**톰캣이 뜰 때마다 각자 돈다.** 배포용 인스턴스와 팀원들의 로컬 인스턴스가 같은 공유 DB 를
보고 있으면 같은 일을 여러 번 한다.

배포해 두는 동안에는 **한 대만 띄우거나**, 팀원들이 각자 로컬 DB 를 쓰도록 하는 편이 낫다.
발표용으로 잠깐 띄우는 거라면 신경 쓰지 않아도 된다.

### 6. 주소 뒤에 `/spec_odyssey_war_exploded/` 가 붙는다 — 그냥 들어가면 404

10/11 에 실제로 겪은 것이다. IntelliJ 로 띄우면 컨텍스트 경로가 WAR 이름이 되어
`https://○○○.trycloudflare.com/` 은 404 가 나고 `…/spec_odyssey_war_exploded/` 로
들어가야 열린다. 시연에서 이 주소를 읽어 주기는 곤란하다.

**IntelliJ** — Run > Edit Configurations > Tomcat > Deployment 탭에서
Application context 를 `/` 로 바꾼다.
**독립 톰캣** — WAR 를 `ROOT.war` 로 이름을 바꿔 `webapps\` 에 넣는다.

둘 다 안 해도 **동작은 한다.** 화면 링크는 전부 `${pageContext.request.contextPath}` 로
만들고, 공유 링크와 캐릭터 주소도 컨텍스트 경로를 포함하도록 테스트로 고정해 뒀다.
주소가 길어질 뿐이다.

### 7. 학교 네트워크가 터널 포트를 막을 수 있다

cloudflared 는 **7844 포트**로 밖에 연결한다. 학교 와이파이가 이걸 막으면 터널이 아예 안 뜬다.
DB 포트도 마찬가지다. **하루 전에 그 네트워크에서 한 번 켜 보고,** 막히면 휴대폰 핫스팟으로 한다.

---

## 덤 — 터널로 띄우면 같이 고쳐지는 것

**공유 링크의 "복사" 버튼이 이제 동작한다.** `navigator.clipboard` 는 https 나 localhost 에서만
있어서, 지금까지 IP 주소(http)로 접속하면 복사가 안 됐다(`share-links.jsp` 에 그 주석이 있다).
터널은 https 라 그냥 된다.

---

## 띄운 뒤 확인할 것

순서대로 한 번씩 눌러 본다. 하나라도 어긋나면 위의 "걸리는 것들"을 다시 본다.

- [ ] `https://내도메인/` 이 열리고 자물쇠가 보인다
- [ ] **로그인이 된다** — 여기서 403 이 나면 `httpHostHeader` 문제다
- [ ] 개발자 도구 → Application → Cookies → `JSESSIONID` 에 **Secure 체크**가 있다
- [ ] 공유 링크를 발급하면 주소가 **`https://내도메인/share/…`** 다 (포트가 안 붙는다)
- [ ] 그 주소를 **휴대폰 데이터**로 열어 본다 (같은 와이파이가 아닌 곳에서)
- [ ] 공유 링크 화면의 **복사 버튼**이 동작한다
- [ ] 서류 보관함에 PDF 하나를 올려 본다
- [ ] 격차 분석을 한 번 돌려 본다 — 몇 초 걸리는지 재 둔다
- [ ] 관리자 → 감사 로그에 **접속자 IP 가 127.0.0.1 이 아니다**

### 시연 당일에 더 챙길 것

- **절전 모드를 끈다** — `powercfg /change standby-timeout-ac 0`. PC 가 자면 터널이 끊긴다
- **PC · 톰캣 · cloudflared 창 셋 중 하나만 꺼져도 접속이 끊긴다**
- **세션은 30분 무활동이면 끝난다.** 시연 직전에 다시 로그인한다
- **임시 터널은 켤 때마다 주소가 바뀐다.** 그날 켠 주소로 공유 링크를 다시 발급한다
- **끝나면 cloudflared 창을 닫는다.** 켜져 있는 동안은 누구나 들어올 수 있다
- 테스트로 만든 계정은 탈퇴 처리한다

---

## 끌 때

```powershell
# 창에서 띄웠으면 Ctrl+C

# 서비스로 등록했으면
cloudflared service uninstall

# 터널 자체를 지우려면 (DNS 연결도 같이 정리된다)
cloudflared tunnel delete spec-odyssey
```

---

## 보안 — 넘어가기 전에

- **`config.yml` 과 `*.json` 자격 증명은 커밋하지 않는다.** `.gitignore` 에 넣어 뒀다.
  자격 증명 파일은 그 터널을 마음대로 쓸 수 있는 열쇠다.
- **주소를 공개하면 가입 화면도 공개된다.** 발표용으로 잠깐만 띄울 거라면,
  클라우드플레어 대시보드의 **Zero Trust → Access** 로 이메일 인증을 앞에 걸어 두는
  방법이 있다. 다만 면접관 공유 링크(`/share/…`)는 계정 없이 열려야 하므로
  그 경로는 예외로 빼야 한다.
- **`.env` 는 배포할 PC 에만 둔다.** 터널은 파일을 노출하지 않지만, 그 PC 가 공개된다는 뜻이다.

---

## 참고 문서

- [`docs/team-todo.md`](team-todo.md) — 넘기기 전에 사람이 해야 하는 일
- [`deploy/cloudflared/config.example.yml`](../deploy/cloudflared/config.example.yml) — 설정 예시
- [`deploy/start-tunnel.ps1`](../deploy/start-tunnel.ps1) — 확인 후 띄우는 스크립트
- [`docs/docker-setup.md`](docker-setup.md) — MySQL 을 컨테이너로 띄우기
