# 시연용 공개 주소 — Cloudflare 터널

날짜: 2026-10-11
관련 커밋: 이 일지만 올린 커밋 1개 (코드 변경 없음)

## 1. 개요

이틀 뒤 시연에서 공유 링크를 밖에서 열 수 있어야 한다. 지금은 `localhost`로만 접속돼서 공유 주소도 `http://localhost:8080/...`로 나온다.

검토한 방법:

| 방법 | 결론 |
| --- | --- |
| Cloudflare 터널 (PC에서 돌리고 바깥 주소만 연다) | **채택.** 학교 PC를 켜 두고 시연한다 |
| 클라우드 서버 (AWS Lightsail) | PC를 꺼도 돌지만 비용과 설정 시간이 든다 |
| 오라클 클라우드 무료 | 무료지만 가입 거절·서버 재고 부족 위험이 있어 이틀 안에는 불확실하다 |
| 팀 DB 서버에 같이 올리기 | 서버 주인의 협조가 필요하다. 시연 뒤에도 링크를 살려 둘 거면 다시 검토한다 |

터널은 PC를 켜 둘 필요를 없애 주지 않는다. 앱은 여전히 그 PC의 Tomcat에서 돈다.
터널이 해결하는 건 "밖에서 들어올 길"이다. 학교 네트워크는 포트포워딩이 안 되는데, 터널은 PC가 밖으로 연결을 먼저 열어 두는 방식이라 공유기 설정 없이 HTTPS 주소가 생긴다.

## 2. 코드는 바꾸지 않았다

`ShareLinksServlet.shareBaseUrl()`이 "지금 접속한 주소 + `/share/`"로 링크를 만든다.
그래서 공개 주소로 접속하면 링크도 그 주소가 된다. DB에는 토큰만 있어서 이미 발급한 링크도 새 주소로 다시 표시된다.

## 3. Tomcat 설정 한 줄 (저장소 밖)

터널 뒤에서는 Tomcat이 요청을 `http`로 알아서 링크가 `http://…`로 만들어진다.
`server.xml`의 `<Host>` 안에 아래 한 줄을 넣으면 `https://…`로 나온다.
cloudflared는 같은 PC(127.0.0.1)에서 붙으므로 내부 주소만 믿는 기본값 그대로 안전하다.

```xml
<Valve className="org.apache.catalina.valves.RemoteIpValve"
       protocolHeader="X-Forwarded-Proto" remoteIpHeader="X-Forwarded-For" />
```

집 PC는 `C:\Tomcat\apache-tomcat-10.1.60\conf\server.xml`에 넣었다. 원본은 `server.xml.bak-20261011`로 백업했다.

## 4. 검증

테스트 Tomcat(8090)에 같은 설정을 넣고 `cloudflared tunnel --url http://localhost:8090`로 바깥 주소를 열어 확인했다.

- 공개 주소로 회원가입 → 로그인 → 프로필 저장 → 공유 링크 발급까지 정상. 다른 사이트에서 온 요청을 막는 Origin 검사도 통과했다.
- 공유 링크가 `https://○○○.trycloudflare.com/share/토큰`으로 만들어졌다. 세션 쿠키에도 Secure가 붙었다.
- 로그인 없는 상태로 그 링크를 열어 지원자 이력 화면이 보이는 것을 스크린샷으로 확인했다.
- IntelliJ Tomcat(8080)에서도 터널로 접속되는 것을 확인했다.
  - 주소 뒤에 `/spec_odyssey_war_exploded/`를 붙여야 열린다. 그냥 들어가면 404가 났다.
- 테스트 계정은 탈퇴 처리했다.

## 5. 학교 PC에서 할 일 (시연 당일)

1. cloudflared 설치: `winget install Cloudflare.cloudflared`
2. 학교 PC Tomcat의 `conf\server.xml`에 3번의 Valve 한 줄을 넣는다(넣기 전에 백업).
3. IntelliJ에서 Application context를 `/`로 바꾼다.
   - 위치: Run > Edit Configurations > Tomcat > Deployment 탭
   - 바꾸지 않으면 주소 뒤에 `/spec_odyssey_war_exploded/`가 붙는다.
4. Tomcat을 실행한다.
5. PowerShell에서 `cloudflared tunnel --url http://localhost:8080`을 실행하고 창을 계속 열어 둔다.
6. 출력 표 안의 `https://○○○.trycloudflare.com` 주소로 접속해 로그인하고 공유 링크를 발급한다.
7. 절전 모드를 끈다: `powercfg /change standby-timeout-ac 0`

주의:
- 터널을 다시 켤 때마다 주소가 바뀐다. 시연 직전에 켠 주소를 쓴다.
- 학교 와이파이가 DB 포트(8306)나 터널 포트(7844)를 막을 수 있다. 하루 전에 한 번 켜 보고, 막히면 휴대폰 핫스팟으로 한다.
- PC, Tomcat, cloudflared 창 중 하나만 꺼져도 접속이 끊긴다.
- 세션은 30분 동안 아무것도 안 하면 끝난다. 시연 직전에 다시 로그인한다.
- 시연이 끝나면 cloudflared 창을 닫는다. 켜져 있는 동안은 누구나 들어올 수 있다.

## 6. 다음에 할 일

- 시연 뒤에도 링크를 살려 둘 거면 팀 DB 서버나 클라우드 서버에 올리는 것을 다시 검토한다.
- 10/10 일지 6번 목록은 그대로 남아 있다.
