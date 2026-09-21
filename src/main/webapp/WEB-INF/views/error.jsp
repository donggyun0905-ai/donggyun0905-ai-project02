<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="오류 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>일시적인 오류가 발생했습니다</h1>
<p>요청을 처리하는 중 문제가 생겼습니다. 잠시 후 다시 시도해주세요.</p>
<p><a href="${pageContext.request.contextPath}/">처음으로</a></p>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
