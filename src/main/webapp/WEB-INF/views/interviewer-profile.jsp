<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="내 프로필 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>내 프로필</h1>
<p class="muted">면접관 계정입니다. 회사명은 지원자 비교 화면에 표시되고, 지원자에게는 보이지 않습니다.</p>

<c:if test="${not empty notice}">
    <p class="pill" style="margin-top:12px;"><c:out value="${notice}" /></p>
</c:if>
<c:if test="${not empty errorMessage}">
    <p class="error-message" style="margin-top:12px;"><c:out value="${errorMessage}" /></p>
</c:if>

<div class="card" style="margin-top:16px;">
    <form method="post" action="${pageContext.request.contextPath}/interviewer/profile">
        <p><label>아이디</label><input type="text" value="<c:out value='${user.loginId}' />" readonly></p>
        <p><label for="name">이름</label><input type="text" id="name" name="name" maxlength="50" required value="<c:out value='${empty param.name ? user.name : param.name}' />"></p>
        <p><label for="email">이메일 (선택)</label><input type="email" id="email" name="email" value="<c:out value='${user.email}' />"></p>
        <p><label for="companyName">회사명 (선택)</label><input type="text" id="companyName" name="companyName" maxlength="100" value="<c:out value='${company}' />"></p>
        <button type="submit">저장</button>
    </form>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
