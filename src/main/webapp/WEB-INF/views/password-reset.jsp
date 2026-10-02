<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="비밀번호 찾기 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>비밀번호 찾기</h1>
    <p class="muted">가입할 때 받은 <strong>복구 코드</strong>로 새 비밀번호를 정합니다. 코드를 받은 적이 없거나 잃어버렸다면 로그인할 수 없는 상태에서는 직접 해결할 수 없으니 운영 담당자(팀)에게 문의해 주세요.</p>

    <c:if test="${not empty errorMessage}">
        <p class="error-message"><c:out value="${errorMessage}" /></p>
    </c:if>

    <div class="card">
        <form action="${pageContext.request.contextPath}/password-reset" method="post">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <p><label for="loginId">아이디</label><input type="text" id="loginId" name="loginId" required value="<c:out value='${param.loginId}' />"></p>
            <p><label for="recoveryCode">복구 코드</label><input type="text" id="recoveryCode" name="recoveryCode" required autocomplete="off" placeholder="XXXX-XXXX-XXXX-XXXX" maxlength="40"></p>
            <p><label for="newPassword">새 비밀번호</label><input type="password" id="newPassword" name="newPassword" minlength="8" maxlength="100" autocomplete="new-password" required></p>
            <p><label for="newPasswordConfirm">새 비밀번호 확인</label><input type="password" id="newPasswordConfirm" name="newPasswordConfirm" minlength="8" maxlength="100" autocomplete="new-password" required></p>
            <button type="submit" style="width:100%; margin-top:6px;">비밀번호 바꾸기</button>
        </form>
    </div>
    <p><a href="${pageContext.request.contextPath}/login">로그인으로 돌아가기</a></p>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
