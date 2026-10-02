<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 직무 기술 트렌드 재집계 - 스펙 오디세이" scope="request" />
<c:set var="adminTab" value="trend" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>🛠 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>
<h2>직무 기술 트렌드(JOB_SKILL_TREND) 재집계</h2>
<p class="muted">JOB_POSTING.tech_stack을 규칙 기반(LLM 없음)으로 다시 집계합니다. 관리자 계정(로그인 아이디가 ADMIN_LOGIN_ID)만 볼 수 있습니다.</p>

<c:if test="${not empty resultMessage}">
    <div class="banner"><span><c:out value="${resultMessage}" /></span></div>
</c:if>

<div class="card">
    <h2>현재 적재 현황</h2>
    <div class="row" style="margin-top:10px; gap:24px;">
        <span>전체 행 수 <strong><c:out value="${totalRows}" /></strong></span>
        <span>집계된 월 수 <strong><c:out value="${periodCount}" /></strong></span>
        <span>최신 집계월 <strong><c:out value="${empty latestPeriod ? '없음' : latestPeriod}" /></strong></span>
    </div>
    <form method="post" style="margin-top:14px;">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <button type="submit">지금 다시 집계하기</button>
    </form>
</div>

<div class="card">
    <h2>최신 집계월 상위 기술 언급 순위</h2>
    <c:choose>
        <c:when test="${not empty topRows}">
            <table style="margin-top:10px;">
                <tr><th>직무</th><th>기술</th><th>언급 공고 수</th><th>비율(%)</th></tr>
                <c:forEach var="row" items="${topRows}">
                    <tr>
                        <td><c:out value="${row.jobName}" /></td>
                        <td><c:out value="${row.skillName}" /></td>
                        <td><c:out value="${row.mentionCount}" /></td>
                        <td><c:out value="${row.mentionRatio}" /></td>
                    </tr>
                </c:forEach>
            </table>
        </c:when>
        <c:otherwise>
            <p class="muted">아직 집계된 데이터가 없습니다. 위 버튼으로 먼저 집계해 주세요.</p>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
