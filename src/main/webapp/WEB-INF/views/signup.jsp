<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>회원가입 - 스펙 오디세이</title>
</head>
<body>
<h1>회원가입</h1>

<c:if test="${not empty errorMessage}">
    <p style="color:red;">${errorMessage}</p>
</c:if>

<form action="${pageContext.request.contextPath}/register" method="post">
    <p><label>아이디 <input type="text" name="loginId" required></label></p>
    <p><label>비밀번호 <input type="password" name="password" required></label></p>
    <p><label>이메일 (선택) <input type="email" name="email"></label></p>
    <p><label>전공 <input type="text" name="major"></label></p>
    <p><label>학년 <input type="text" name="grade"></label></p>
    <p><label>관심 분야 <input type="text" name="interestField"></label></p>
    <button type="submit">가입하기</button>
</form>

<p><a href="${pageContext.request.contextPath}/login">이미 계정이 있으신가요? 로그인</a></p>
</body>
</html>
