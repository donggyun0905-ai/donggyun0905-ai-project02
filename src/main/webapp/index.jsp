<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>스펙 오디세이</h1>
<p class="muted">취업 준비의 부족한 부분을 진단하고, 목표 직무까지 가는 순서 있는 여정을 제시합니다.</p>

<div class="card">
    <p class="row">
        <a class="btn" href="${pageContext.request.contextPath}/login">로그인</a>
        <a class="btn secondary" href="${pageContext.request.contextPath}/register">회원가입</a>
    </p>
    <p class="muted">로그인하면 내 프로필과 로드맵을 볼 수 있습니다.</p>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
