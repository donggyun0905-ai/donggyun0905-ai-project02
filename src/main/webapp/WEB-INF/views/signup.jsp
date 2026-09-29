<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="회원가입 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>회원가입</h1>

    <c:if test="${not empty errorMessage}">
        <p class="error-message">${errorMessage}</p>
    </c:if>

    <div class="card">
    <form action="${pageContext.request.contextPath}/register" method="post">
        <p><label>아이디 </label><input type="text" name="loginId" required></p>
        <p><label>비밀번호 </label><input type="password" name="password" required></p>
        <p><label>이메일 (선택) </label><input type="email" name="email"></p>
        <p><label>전공 </label><input type="text" name="major"></p>
        <p><label>학년 </label><input type="text" name="grade"></p>
        <p><label>관심 분야 </label><input type="text" name="interestField"></p>
        <button type="submit"style="width:100%; margin-top:6px;">가입하기</button>
    </form>
</div>

    <p><a href="${pageContext.request.contextPath}/login">이미 계정이 있으신가요? 로그인</a></p>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
