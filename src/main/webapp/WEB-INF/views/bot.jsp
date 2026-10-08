<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="오셍이들 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<%-- 오셍이들 — 데스크톱 캐릭터 소개 · 내려받기 · 연결 · 연결된 PC (BotServlet, js/companion.js).
     값은 서블릿이 담고 여기서는 출력만 한다. 연결 버튼·PC 목록은 스크립트가 /companion/* 를 부른다. --%>

<section class="card bot-hero">
    <img class="bot-hero-img" src="${ctx}/image/bot/tier${myTierIndex}.png" alt="지금 내 등급의 오셍이" width="240">
    <div class="bot-hero-body">
        <h1>오셍이들</h1>
        <p class="bot-lead">바탕화면 구석에서 오늘 할 일을 말풍선으로 알려 주는 스펙 오디세이 캐릭터예요.</p>
        <p class="muted">사이트를 켜 두지 않아도 남은 미션, 끊기기 직전인 연속 기록, 다가오는 D-day, 면접관이 내 이력을 본 소식을 먼저 챙겨 줘요.
            등급이 오르면 오셍이도 함께 자라요. 윈도우 PC에서 쓸 수 있어요.</p>

        <div class="card companion-card" id="companionCard" data-base="${ctx}" data-csrf="${csrfToken}">
            <div class="row" style="gap:8px;">
                <%-- 켜기: 이 PC가 이미 연결돼 있으면 설치된 캐릭터를 켜기만 한다(새 연결 없음), 아니면 연결까지 --%>
                <button type="button" class="btn" id="companionStart">캐릭터 켜기</button>
                <button type="button" class="btn secondary" id="companionLaunch">캐릭터 연결</button>
                <c:choose>
                    <c:when test="${not empty releaseVersion}">
                        <a class="btn secondary" id="companionDownloadTop" href="${ctx}/companion/download">
                            설치 파일 내려받기 <span class="muted">(${releaseVersion} · ${releaseSize})</span></a>
                    </c:when>
                    <c:otherwise>
                        <span class="muted">설치 파일 준비 중이에요.</span>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="companion-ask" id="companionAsk" hidden>
                <p><strong>캐릭터 프로그램이 아직 설치되지 않은 것 같아요.</strong> 내려받을까요?</p>
                <p class="muted">내려받은 <code>SpecOdysseyCompanion-Setup.exe</code>를 실행하면 설치가 끝나고 오셍이가 나타나요.
                    그다음 "캐릭터 연결"을 한 번 더 누르면 내 계정과 이어져요.</p>
                <div class="row" style="gap:8px;">
                    <a class="btn" id="companionDownload" href="#" rel="noopener">내려받기</a>
                    <button type="button" class="btn secondary" id="companionRetry">이미 설치했어요</button>
                </div>
            </div>
            <p class="companion-status" id="companionStatus" role="status" aria-live="polite"></p>
            <h3 class="bot-sub">연결된 PC</h3>
            <ul class="item-list" id="companionDevices"><li class="muted">불러오는 중…</li></ul>
        </div>
    </div>
</section>

<section class="card">
    <h2>오셍이는 이런 걸 알려 줘요</h2>
    <div class="bot-features">
        <div class="bot-feature"><span class="ic ic-square-check" aria-hidden="true"></span>
            <strong>오늘의 미션</strong><p class="muted">남은 문제 수를 알려 주고, 풀면 말풍선이 스스로 바뀌거나 사라져요.</p></div>
        <div class="bot-feature"><span class="ic ic-trending-up" aria-hidden="true"></span>
            <strong>연속 기록 지키기</strong><p class="muted">저녁(기본 8시)까지 미션을 안 했으면 기록이 끊기기 전에 알려 줘요.</p></div>
        <div class="bot-feature"><span class="ic ic-mail" aria-hidden="true"></span>
            <strong>사이트 알림</strong><p class="muted">댓글·답글, 면접관이 내 공유 링크를 연 소식. 말풍선에서 바로 읽음 처리돼요.</p></div>
        <div class="bot-feature"><span class="ic ic-map" aria-hidden="true"></span>
            <strong>로드맵 다음 단계</strong><p class="muted">지금 해야 할 단계를 짚어 주고, 단계를 끝내면 칭찬해 줘요.</p></div>
        <div class="bot-feature"><span class="ic ic-calendar" aria-hidden="true"></span>
            <strong>D-day</strong><p class="muted">시험·공채 마감 3일 전부터 놓치지 않게 알려 줘요.</p></div>
        <div class="bot-feature"><span class="ic ic-bar-chart" aria-hidden="true"></span>
            <strong>하루 요약 · 트렌드</strong><p class="muted">아침(기본 9시)에 오늘 할 일을 한 번에, 한가할 때는 요즘 뜨는 기술을 보여 줘요.</p></div>
        <div class="bot-feature"><span class="ic ic-pencil" aria-hidden="true"></span>
            <strong>연습장</strong><p class="muted">로드맵 화면의 연습장과 같은 내용이에요. 바탕화면에서 바로 적고 저장해요.</p></div>
        <div class="bot-feature"><span class="ic ic-sparkles" aria-hidden="true"></span>
            <strong>등급 축하</strong><p class="muted">점수가 쌓여 등급이 오르면 오셍이가 새 모습으로 축하해 줘요.</p></div>
    </div>
</section>

<section class="card">
    <h2>등급이 오르면 오셍이도 자라요</h2>
    <p class="muted" style="margin-top:4px;">테두리가 있는 것이 지금 내 오셍이예요.</p>
    <div class="bot-tiers">
        <c:forEach var="t" items="${tierCards}">
            <figure class="bot-tier ${t.mine ? 'mine' : ''}">
                <img src="${ctx}/image/bot/tier${t.index}.png" alt="<c:out value='${t.tierName}' /> 오셍이" loading="lazy" width="240">
                <figcaption>
                    <strong><c:out value="${t.titleName}" /></strong>
                    <span class="muted"><c:out value="${t.tierName}" /> · ${t.minScore}점부터</span>
                    <c:if test="${t.mine}"><span class="bot-mine">지금 나</span></c:if>
                </figcaption>
            </figure>
        </c:forEach>
    </div>
</section>

<section class="card">
    <h2>시작하는 법</h2>
    <ol class="bot-steps">
        <li><strong>설치 파일 내려받기</strong> — 위의 [설치 파일 내려받기]를 눌러 <code>SpecOdysseyCompanion-Setup.exe</code>를 받아 실행해요. 관리자 권한은 필요 없어요.</li>
        <li><strong>캐릭터 켜기</strong> — 설치가 끝나면 이 화면에서 [캐릭터 켜기]를 눌러요. 처음이면 내 계정과 연결까지 하고 인사해요.
            꺼 둔 오셍이를 다시 부를 때도 같은 버튼이에요. 다른 PC 연결을 정리하고 새로 잇고 싶을 때만 [캐릭터 연결]을 써요.</li>
        <li><strong>그다음은 알아서</strong> — 이 PC의 브라우저에서 다른 계정으로 로그인하면 오셍이도 그 계정으로 옮겨 가요.
            로그아웃하면 쉬고 있다가, 다시 로그인하면 자동으로 이어져요. 연결을 다시 누를 필요가 없어요.</li>
    </ol>
    <p class="muted" style="margin-bottom:0;">오셍이를 오른쪽 클릭하면 조용히(1시간·오늘은 그만), 숨기기, 연습장, 설정, 연결 해제를 고를 수 있어요.
        한 PC에는 연결이 하나만 남아요 — 다시 연결하면 예전 연결은 자동으로 정리돼요.</p>
</section>

<style>
    /* 오셍이들 화면 전용 (2026-10-08) */
    .bot-hero { display: flex; gap: 24px; align-items: flex-start; }
    .bot-hero-img { width: 200px; height: auto; flex-shrink: 0; filter: drop-shadow(0 6px 10px rgba(58,47,36,0.18)); }
    .bot-hero-body { flex: 1; min-width: 0; }
    .bot-lead { font-size: 1.05rem; margin: 6px 0; }
    .bot-hero .companion-card { margin: 14px 0 0; box-shadow: none; background: #fbf3e2; }
    .bot-sub { margin: 16px 0 6px; font-size: 0.95rem; }
    .companion-ask { margin-top: 12px; padding: 12px 14px; border-radius: 8px; border-left: 3px solid var(--gold); background: #f6e9cf; }
    .companion-ask p { margin: 0 0 8px; }
    .companion-status { margin: 10px 0 0; font-size: 0.88rem; color: var(--ink-soft); min-height: 1em; }
    .companion-this-pc { margin-left: 6px; font-size: 0.72rem; font-weight: 700; color: #fff; background: var(--teal);
                         border-radius: 999px; padding: 1px 7px; }
    .bot-features { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; margin-top: 12px; }
    .bot-feature { border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; background: var(--card-bg); }
    .bot-feature .ic { color: var(--gold); margin-right: 4px; }
    .bot-feature p { margin: 6px 0 0; }
    .bot-tiers { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-top: 12px; }
    .bot-tier { margin: 0; text-align: center; border: 1px solid var(--border); border-radius: 12px; padding: 10px 8px; background: var(--card-bg); }
    .bot-tier img { width: 100%; max-width: 150px; height: auto; }
    .bot-tier figcaption { display: flex; flex-direction: column; gap: 2px; margin-top: 6px; }
    .bot-tier.mine { border: 2px solid var(--gold); background: #fbf3e2; }
    .bot-mine { align-self: center; margin-top: 4px; font-size: 0.72rem; font-weight: 700; color: #fff; background: var(--gold);
                border-radius: 999px; padding: 1px 8px; }
    .bot-steps { margin: 10px 0 12px; padding-left: 20px; }
    .bot-steps li { margin: 6px 0; }
    @media (max-width: 720px) {
        .bot-hero { flex-direction: column; align-items: center; }
        .bot-hero-img { width: 150px; }
        .bot-tiers { grid-template-columns: repeat(3, 1fr); }
    }
</style>
<script src="${ctx}/js/companion.js" defer></script>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
