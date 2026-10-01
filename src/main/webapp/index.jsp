<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- PDF 화면설계에는 별도 랜딩(소개) 화면이 없다 — 첫 화면이 바로 로그인이다.
     c:redirect는 "/"로 시작하는 url을 이미 컨텍스트 루트 기준으로 해석한다(JSTL 스펙) —
     여기에 contextPath를 또 붙이면 "/스펙오디세이/스펙오디세이/login"처럼 두 번 붙어서
     404가 난다(로고 클릭 시 에러 화면이 뜨던 원인, 2026-09-30 발견). --%>
<c:redirect url="/login" />
