<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>Spec Odyssey</h1>
<p><a href="${pageContext.request.contextPath}/login">로그인</a></p>
<p><a href="${pageContext.request.contextPath}/register">회원가입</a></p>
<p><a href="${pageContext.request.contextPath}/profile">내 프로필</a> (로그인 필요)</p>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
