<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>${empty pageTitle ? '스펙 오디세이' : pageTitle}</title>
</head>
<body>
<header>
    <a href="${pageContext.request.contextPath}/">스펙 오디세이</a>
    <c:if test="${not empty sessionScope.loginUser}">
        <a href="${pageContext.request.contextPath}/profile">내 프로필</a>
        <a href="${pageContext.request.contextPath}/logout">로그아웃</a>
    </c:if>
</header>
<main>
