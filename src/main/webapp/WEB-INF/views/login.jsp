<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로그인 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>로그인</h1>

<c:if test="${not empty errorMessage}">
    <p style="color:red;">${errorMessage}</p>
</c:if>

<form action="${pageContext.request.contextPath}/login" method="post">
    <p><label>아이디 <input type="text" name="loginId" required></label></p>
    <p><label>비밀번호 <input type="password" name="password" required></label></p>
    <button type="submit">로그인</button>
</form>

<p><a href="${pageContext.request.contextPath}/register">아직 계정이 없으신가요? 회원가입</a></p>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
