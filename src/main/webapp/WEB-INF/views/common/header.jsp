<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="path" value="${pageContext.request.servletPath}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${empty pageTitle ? '스펙 오디세이' : pageTitle}</title>
    <link rel="stylesheet" href="${ctx}/css/style.css">
    <link rel="stylesheet" href="${ctx}/css/icons.css">
    <link rel="stylesheet" href="${ctx}/css/notification.css">
    <c:if test="${simTester}"><link rel="stylesheet" href="${ctx}/css/simulation.css"></c:if>
</head>
<body class="${sideWidgets ? 'has-side' : ''}">
<input type="checkbox" id="nav-toggle">
<header>
    <c:if test="${not empty sessionScope.loginUser}">
        <label for="nav-toggle" class="menu-btn"><span class="ic ic-menu" aria-hidden="true"></span> 목록</label>
    </c:if>
    <a class="brand" href="${ctx}/"><img src="${ctx}/image/logo.png" alt="로고">스펙 오디세이</a>
    <span class="spacer"></span>
    <c:if test="${not empty sessionScope.loginUser}">
        <%-- 알림 버튼 — 등급 배지 왼쪽 (내용은 notification-bell.jsp) --%>
        <jsp:include page="/WEB-INF/views/common/notification-bell.jsp" />
        <%-- 면접관 계정은 점수·등급이 없다 --%>
        <c:if test="${not empty currentTier and sessionScope.loginUser.userType != 'INTERVIEWER'}">
            <span class="tier-badge">
                <img src="${ctx}${tierLogoPath}" alt="${currentTier.tierName}">
                <strong>${currentTier.tierName}</strong> · ${totalScore}점
            </span>
        </c:if>
        <%-- 테스트 계정(USERS.is_test)에만 — 지난 70일 시뮬레이션 / 일시정지 / 초기화 (SimulationPanelFilter, SimulationServlet) --%>
        <c:if test="${simTester}">
            <div class="sim-panel" id="simPanel" data-endpoint="${ctx}/simulation" data-csrf="${csrfToken}">
                <span class="sim-title">시뮬레이션</span>
                <span class="sim-track" aria-hidden="true"><span class="sim-fill" id="simFill"></span></span>
                <span class="sim-text" id="simText" role="status" aria-live="polite">불러오는 중…</span>
                <%-- 성향 — 고르지 않으면 무작위. 판마다 결과도 무작위로 달라진다 --%>
                <select class="sim-persona" id="simPersona" aria-label="시뮬레이션 성향">
                    <option value="">성향: 무작위</option>
                    <option value="DILIGENT">성실형</option>
                    <option value="STEADY">보통형</option>
                    <option value="ON_OFF">작심삼일형</option>
                </select>
                <%-- 목표 점수 — 직접 넣거나 등급으로 바로 채운다. 이 점수에 닿을 때까지 하루씩 진행 --%>
                <input type="number" class="sim-target" id="simTarget" min="1" max="100000" step="50"
                       placeholder="목표 점수" aria-label="목표 점수" autocomplete="off">
                <select class="sim-tier" id="simTierPick" aria-label="등급으로 목표 점수 정하기">
                    <option value="">등급</option>
                    <c:forEach var="t" items="${simTiers}">
                        <c:if test="${t.minScore > 0}"><option value="${t.minScore}"><c:out value="${t.tierName}" /> ${t.minScore}</option></c:if>
                    </c:forEach>
                </select>
                <button type="button" class="sim-btn" data-action="start">시작</button>
                <button type="button" class="sim-btn" data-action="pause">일시정지</button>
                <button type="button" class="sim-btn sim-danger" data-action="reset">초기화</button>
                <button type="button" class="sim-btn sim-follow" data-action="follow" aria-pressed="false"
                        title="켜면 방금 바뀐 화면(대시보드·로드맵)으로 따라가며 천천히 진행합니다">화면 따라가기</button>
                <span class="sim-day" id="simDay" hidden></span>
                <span class="sim-msg" id="simMsg" hidden></span>
            </div>
            <script src="${ctx}/js/simulation.js" defer></script>
        </c:if>
    </c:if>
</header>

<c:if test="${not empty sessionScope.loginUser}">
<div class="nav-drawer">
    <label for="nav-toggle" style="position:absolute; inset:0; cursor:default;"></label>
    <div class="nav-drawer-panel">
        <div class="drawer-head">목록 <label for="nav-toggle" aria-label="닫기"><span class="ic ic-x" aria-hidden="true"></span></label></div>

        <c:choose>
        <%-- 면접관 계정은 공유받은 이력·지원자 비교·내 프로필만 쓴다 (RoleFilter) --%>
        <c:when test="${sessionScope.loginUser.userType == 'INTERVIEWER'}">
        <div class="nav-group-title">면접관</div>
        <a href="${ctx}/interviewer/shared"><span class="ic ic-mail" aria-hidden="true"></span> 공유받은 이력</a>
        <a href="${ctx}/interviewer/compare"><span class="ic ic-scale" aria-hidden="true"></span> 지원자 비교</a>

        <div class="nav-group-title">계정</div>
        <a href="${ctx}/interviewer/profile"><span class="ic ic-user" aria-hidden="true"></span> 내 프로필</a>
        <form action="${ctx}/logout" method="post" class="logout-form">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <button type="submit"><span class="ic ic-logout" aria-hidden="true"></span> 로그아웃</button>
        </form>
        </c:when>
        <c:otherwise>
        <div class="nav-group-title">여정</div>
        <a href="${ctx}/dashboard" class="${path == '/dashboard' ? 'active' : ''}"><span class="ic ic-dashboard" aria-hidden="true"></span> 대시보드</a>
        <a href="${ctx}/roadmap" class="${path == '/roadmap' ? 'active' : ''}"><span class="ic ic-map" aria-hidden="true"></span> 내 로드맵</a>
        <a href="${ctx}/mission" class="${path == '/mission' ? 'active' : ''}"><span class="ic ic-square-check" aria-hidden="true"></span> 오늘의 미션</a>
        <a href="${ctx}/gap-analysis" class="${path == '/gap-analysis' ? 'active' : ''}"><span class="ic ic-trending-up" aria-hidden="true"></span> 격차 분석</a>
        <a href="${ctx}/job-discovery" class="${path == '/job-discovery' ? 'active' : ''}"><span class="ic ic-search" aria-hidden="true"></span> 직무 찾기</a>

        <div class="nav-group-title">성장 도구</div>
        <%@ include file="/WEB-INF/views/common/companion-nav.jspf" %>
        <a href="${ctx}/insights" class="${path == '/insights' ? 'active' : ''}"><span class="ic ic-bar-chart" aria-hidden="true"></span> 데이터 인사이트</a>
        <a href="${ctx}/dday" class="${path == '/dday' ? 'active' : ''}"><span class="ic ic-calendar" aria-hidden="true"></span> D-day 알림<c:if test="${not empty navDdayText}"> <span class="nav-badge">${navDdayText}</span></c:if></a>
        <a href="${ctx}/documents" class="${path == '/documents' ? 'active' : ''}"><span class="ic ic-folder" aria-hidden="true"></span> 서류 보관함</a>
        <a href="${ctx}/resume-feedback" class="${path == '/resume-feedback' ? 'active' : ''}"><span class="ic ic-pencil" aria-hidden="true"></span> 자소서 첨삭</a>
        <a href="${ctx}/spec-archive" class="${path.startsWith('/spec-archive') ? 'active' : ''}"><span class="ic ic-book" aria-hidden="true"></span> 스펙 아카이브</a>

        <c:if test="${isAdmin}">
        <div class="nav-group-title">관리</div>
        <a href="${ctx}/admin" class="${path.startsWith('/admin') ? 'active' : ''}"><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</a>
        </c:if>

        <div class="nav-group-title">공유 · 계정</div>
        <a href="${ctx}/share-links" class="${path == '/share-links' ? 'active' : ''}"><span class="ic ic-link" aria-hidden="true"></span> 공유 링크</a>
        <a href="${ctx}/profile" class="${path == '/profile' ? 'active' : ''}"><span class="ic ic-user" aria-hidden="true"></span> 내 프로필</a>
        <form action="${ctx}/logout" method="post" class="logout-form">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <button type="submit"><span class="ic ic-logout" aria-hidden="true"></span> 로그아웃</button>
        </form>
        </c:otherwise>
        </c:choose>
    </div>
</div>
</c:if>

<main class="${mainWide ? 'wide' : ''}${mainFull ? ' full' : ''}">
<%-- FR-111 AI 응답을 받지 못해 대체했을 때의 안내 — AiNoticeFilter가 세션에 담고, 한 번 보여준 뒤 지운다 (2026-10-02, E) --%>
<c:if test="${not empty sessionScope.aiNotice}">
    <c:forEach var="aiMsg" items="${sessionScope.aiNotice}">
    <div class="banner ai-notice" role="status">
        <span><c:out value="${aiMsg.message}" /></span>
        <%-- 시간이 지나면 풀리는 실패일 때만 "다시 시도" (AiRetryServlet). 누르면 응답이 올 때까지 버튼을 잠근다 (NFR-5) --%>
        <c:if test="${aiMsg.retryable}">
        <form action="${pageContext.request.contextPath}/ai-retry" method="post" style="margin:0;"
              onsubmit="var b=this.querySelector('button');b.disabled=true;b.textContent='다시 시도하는 중…';">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="target" value="${aiMsg.retryTarget}">
            <button type="submit" class="secondary" style="white-space:nowrap;">다시 시도</button>
        </form>
        </c:if>
    </div>
    </c:forEach>
    <c:remove var="aiNotice" scope="session" />
</c:if>
