<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${empty pageTitle ? '스펙 오디세이' : pageTitle}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<header>
    <a class="brand" href="${pageContext.request.contextPath}/">스펙 오디세이</a>
    <c:if test="${not empty sessionScope.loginUser}">
<<<<<<< Updated upstream
        <a href="${pageContext.request.contextPath}/profile">내 프로필</a>
        <a href="${pageContext.request.contextPath}/logout">로그아웃</a>
=======
        <nav>
            <c:if test="${not empty currentTier}">
                <span class="tier-badge">
                    <img src="${pageContext.request.contextPath}${tierLogoPath}" alt="${currentTier.tierName}" height="26">
                    <strong>${currentTier.tierName}</strong> · ${totalScore}점
                </span>
            </c:if>
            <a href="${pageContext.request.contextPath}/profile">내 프로필</a>
            <a href="${pageContext.request.contextPath}/roadmap">로드맵</a>
            <a href="${pageContext.request.contextPath}/logout">로그아웃</a>
        </nav>
>>>>>>> Stashed changes
    </c:if>
</header>
<main>
