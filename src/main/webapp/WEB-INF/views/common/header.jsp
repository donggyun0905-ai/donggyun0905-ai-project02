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
</head>
<body>
<input type="checkbox" id="nav-toggle">
<header>
    <c:if test="${not empty sessionScope.loginUser}">
        <label for="nav-toggle" class="menu-btn">☰ 목록</label>
    </c:if>
    <a class="brand" href="${ctx}/"><img src="${ctx}/image/logo.png" alt="로고">스펙 오디세이</a>
    <span class="spacer"></span>
    <c:if test="${not empty sessionScope.loginUser}">
        <c:if test="${not empty currentTier}">
            <span class="tier-badge">
                <img src="${ctx}${tierLogoPath}" alt="${currentTier.tierName}">
                <strong>${currentTier.tierName}</strong> · ${totalScore}점
            </span>
        </c:if>
    </c:if>
</header>

<c:if test="${not empty sessionScope.loginUser}">
<div class="nav-drawer">
    <label for="nav-toggle" style="position:absolute; inset:0; cursor:default;"></label>
    <div class="nav-drawer-panel">
        <div class="drawer-head">목록 <label for="nav-toggle">✕</label></div>

        <c:choose>
        <%-- 면접관 계정은 공유받은 이력·지원자 비교·내 프로필만 쓴다 (RoleFilter) --%>
        <c:when test="${sessionScope.loginUser.userType == 'INTERVIEWER'}">
        <div class="nav-group-title">면접관</div>
        <a href="${ctx}/interviewer/shared">📨 공유받은 이력</a>
        <a href="${ctx}/interviewer/compare">⚖️ 지원자 비교</a>

        <div class="nav-group-title">계정</div>
        <a href="${ctx}/interviewer/profile">👤 내 프로필</a>
        <form action="${ctx}/logout" method="post" class="logout-form">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <button type="submit">↩ 로그아웃</button>
        </form>
        </c:when>
        <c:otherwise>
        <div class="nav-group-title">여정</div>
        <a href="${ctx}/dashboard" class="${path == '/dashboard' ? 'active' : ''}">📊 대시보드</a>
        <a href="${ctx}/roadmap" class="${path == '/roadmap' ? 'active' : ''}">🗺️ 내 로드맵</a>
        <a href="${ctx}/mission" class="${path == '/mission' ? 'active' : ''}">✅ 오늘의 미션</a>
        <a href="${ctx}/gap-analysis" class="${path == '/gap-analysis' ? 'active' : ''}">📈 격차 분석</a>
        <a href="${ctx}/job-discovery" class="${path == '/job-discovery' ? 'active' : ''}">🔍 직무 찾기</a>

        <div class="nav-group-title">성장 도구</div>
        <a href="${ctx}/insights" class="${path == '/insights' ? 'active' : ''}">📉 데이터 인사이트</a>
        <a href="${ctx}/dday" class="${path == '/dday' ? 'active' : ''}">🗓️ D-day 알림<c:if test="${not empty navDdayText}"> <span class="nav-badge">${navDdayText}</span></c:if></a>
        <a href="${ctx}/documents" class="${path == '/documents' ? 'active' : ''}">📁 서류 보관함</a>
        <a href="${ctx}/resume-feedback" class="${path == '/resume-feedback' ? 'active' : ''}">✏️ 자소서 첨삭</a>
        <a href="${ctx}/spec-archive" class="${path.startsWith('/spec-archive') ? 'active' : ''}">📚 스펙 아카이브</a>

        <c:if test="${isAdmin}">
        <div class="nav-group-title">관리</div>
        <a href="${ctx}/admin" class="${path == '/admin' || path == '/admin/job-skill-trend' ? 'active' : ''}">🛠 관리자</a>
        </c:if>

        <div class="nav-group-title">공유 · 계정</div>
        <a href="${ctx}/share-links" class="${path == '/share-links' ? 'active' : ''}">🔗 공유 링크</a>
        <a href="${ctx}/profile" class="${path == '/profile' ? 'active' : ''}">👤 내 프로필</a>
        <form action="${ctx}/logout" method="post" class="logout-form">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <button type="submit">↩ 로그아웃</button>
        </form>
        </c:otherwise>
        </c:choose>
    </div>
</div>
</c:if>

<main class="${mainWide ? 'wide' : ''}">
