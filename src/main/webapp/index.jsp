<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- PDF 화면설계에는 별도 랜딩(소개) 화면이 없다 — 첫 화면이 바로 로그인이다. --%>
<c:redirect url="${pageContext.request.contextPath}/login" />
