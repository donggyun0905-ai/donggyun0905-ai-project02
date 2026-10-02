<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로그인 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>로그인</h1>

    <c:if test="${not empty errorMessage}">
        <p class="error-message">${errorMessage}</p>
    </c:if>

    <div class="card">
        <form action="${pageContext.request.contextPath}/login" method="post">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <p><label>아이디</label><input type="text" name="loginId" required></p>
            <p><label>비밀번호</label><input type="password" name="password" required></p>
            <button type="submit" style="width:100%; margin-top:6px;">로그인</button>
        </form>
    </div>

    <p><a href="${pageContext.request.contextPath}/register">아직 계정이 없으신가요? 회원가입</a></p>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
