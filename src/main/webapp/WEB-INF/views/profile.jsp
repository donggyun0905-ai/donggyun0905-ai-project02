<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="내 프로필 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>내 프로필</h1>

<c:if test="${not empty currentTier}">
    <div class="card row" style="gap:20px;">
        <img src="${pageContext.request.contextPath}${tierLogoPath}" alt="${currentTier.tierName}" height="70">
        <p style="margin:0;">
            현재 등급: <strong>${currentTier.tierName}</strong> (${currentTier.titleName})<br>
            누적 점수: <strong>${totalScore}점</strong>
            <c:if test="${not empty currentTier.maxScore}">
                / 다음 등급까지 ${currentTier.maxScore - totalScore + 1}점
            </c:if>
        </p>
    </div>
</c:if>

<c:if test="${not empty errorMessage}">
    <p class="error-message"><c:out value='${errorMessage}' /></p>
</c:if>

<%-- FR-114 — 어디까지 채웠고 다음에 무엇을 할지 --%>
<%@ include file="/WEB-INF/views/common/next-steps.jspf" %>

<%@ include file="/WEB-INF/views/profile/_basic.jspf" %>

<%@ include file="/WEB-INF/views/profile/_education.jspf" %>

<%@ include file="/WEB-INF/views/profile/_documents.jspf" %>

<%@ include file="/WEB-INF/views/profile/_specs.jspf" %>

<%@ include file="/WEB-INF/views/profile/_projects.jspf" %>

<%@ include file="/WEB-INF/views/profile/_skills.jspf" %>

<%@ include file="/WEB-INF/views/profile/_ai-usage.jspf" %>

<%@ include file="/WEB-INF/views/profile/_companion.jspf" %>

<c:set var="passwordAction" value="/profile/password" />
<%@ include file="/WEB-INF/views/common/password-change.jspf" %>
<%@ include file="/WEB-INF/views/profile/_withdraw.jspf" %>

<%-- 기술·스펙 이름 검사 안내 (FR-23·25, 2026-10-06) — 공통 CSS를 건드리지 않게 이 화면에만 둔다 --%>
<style>
    .input-check { display: none; }
    .input-check.on { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 6px 0 2px; padding: 7px 10px;
        border-radius: 6px; border-left: 3px solid var(--ink-soft); background: var(--card-bg); font-size: 0.85rem; color: var(--ink-soft); }
    .input-check.is-ok { border-left-color: var(--teal); color: var(--teal); background: var(--teal-bg); }
    .input-check.is-suggest { border-left-color: var(--gold); color: var(--ink); background: #f6e9cf; }
    .input-check.is-unknown { border-left-color: var(--locked); }
    .input-check.is-gibberish { border-left-color: var(--danger); color: var(--danger); background: var(--danger-bg); }
    .input-check-pick { padding: 3px 10px; border-radius: 999px; border: 1px solid var(--gold); background: #fff;
        color: var(--ink); font-size: 0.82rem; font-weight: 600; cursor: pointer; }
    .input-check-pick:hover { background: var(--gold); color: #fff; }
    input.input-bad { border-color: var(--danger); }
</style>
<script src="${pageContext.request.contextPath}/js/profile-input-check.js" defer></script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
