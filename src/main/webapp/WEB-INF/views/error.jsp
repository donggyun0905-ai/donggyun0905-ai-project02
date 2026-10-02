<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="오류 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>🧭 항해 중 문제가 생겼습니다</h1>
    <div class="card">
        <c:set var="errorStatus" value="${requestScope['jakarta.servlet.error.status_code']}" />
        <c:choose>
            <%-- 403(출처·CSRF 확인 실패)과 413(첨부 용량 초과)은 사용자가 할 수 있는 일이 정해져 있어 메시지를 그대로 보여준다 --%>
            <c:when test="${(errorStatus == 403 or errorStatus == 413) and not empty requestScope['jakarta.servlet.error.message']}">
                <p><c:out value="${requestScope['jakarta.servlet.error.message']}" /></p>
            </c:when>
            <c:otherwise>
                <p>요청을 처리하는 중 문제가 생겼습니다. 잠시 후 다시 시도해주세요.</p>
            </c:otherwise>
        </c:choose>
        <a class="btn" href="${pageContext.request.contextPath}/login">처음으로</a>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
