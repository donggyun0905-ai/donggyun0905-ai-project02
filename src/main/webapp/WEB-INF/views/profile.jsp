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
    <p class="error-message">${errorMessage}</p>
</c:if>

<%@ include file="/WEB-INF/views/profile/_basic.jspf" %>

<%@ include file="/WEB-INF/views/profile/_documents.jspf" %>

<%@ include file="/WEB-INF/views/profile/_specs.jspf" %>

<%@ include file="/WEB-INF/views/profile/_projects.jspf" %>

<%@ include file="/WEB-INF/views/profile/_skills.jspf" %>

<%@ include file="/WEB-INF/views/profile/_ai-usage.jspf" %>

<%@ include file="/WEB-INF/views/profile/_withdraw.jspf" %>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
