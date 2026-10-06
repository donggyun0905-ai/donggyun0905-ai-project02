<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="관리자 · 로드맵 수정 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="roadmap" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<p class="muted">완료 체크가 꼬인 단계를 고치는 지원 도구입니다. 여기서 켜고 끄는 완료 표시는
    <strong>점수·프로필에 영향을 주지 않습니다</strong> — 점수까지 정상 반영하려면 사용자가 화면에서 직접 완료하게 안내하세요.</p>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<div class="card">
    <h2>사용자 찾기</h2>
    <form method="get" action="${pageContext.request.contextPath}/admin/roadmap" class="row" style="margin-top:10px;">
        <input type="text" name="loginId" value="${loginId}" placeholder="로그인 아이디" style="flex:1;">
        <button type="submit">조회</button>
    </form>
</div>

<c:if test="${not empty roadmap}">
    <div class="card">
        <div class="spread">
            <h2><c:out value="${targetUser.loginId}" /> 의 로드맵 (버전 ${roadmap.version})</h2>
        </div>
        <table style="margin-top:10px; width:100%;">
            <tr><th style="text-align:left;">단계</th><th style="text-align:left;">대상</th><th>티어</th><th>완료</th><th></th></tr>
            <c:forEach var="step" items="${steps}" varStatus="st">
                <tr>
                    <td><c:out value="${step.stepType}" /></td>
                    <td><c:out value="${stepLabels[st.index]}" /></td>
                    <td class="muted"><c:out value="${step.tier}" /></td>
                    <td style="text-align:center;">
                        <c:choose>
                            <c:when test="${step.completed}"><span class="chip chip-teal">완료</span></c:when>
                            <c:otherwise><span class="chip chip-locked">미완료</span></c:otherwise>
                        </c:choose>
                    </td>
                    <td style="white-space:nowrap;">
                        <form method="post" action="${pageContext.request.contextPath}/admin/roadmap" class="inline-form">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="loginId" value="${loginId}">
                            <input type="hidden" name="userId" value="${targetUser.id}">
                            <input type="hidden" name="stepId" value="${step.id}">
                            <c:choose>
                                <c:when test="${step.completed}">
                                    <input type="hidden" name="action" value="uncomplete">
                                    <button type="submit" class="link-button">미완료로</button>
                                </c:when>
                                <c:otherwise>
                                    <input type="hidden" name="action" value="complete">
                                    <button type="submit" class="link-button">완료로</button>
                                </c:otherwise>
                            </c:choose>
                        </form>
                        <c:if test="${not step.completed}">
                            <form method="post" action="${pageContext.request.contextPath}/admin/roadmap" class="inline-form"
                                  onsubmit="return confirm('이 단계를 지울까요? 되돌릴 수 없습니다.');">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="loginId" value="${loginId}">
                                <input type="hidden" name="userId" value="${targetUser.id}">
                                <input type="hidden" name="stepId" value="${step.id}">
                                <button type="submit" class="link-button" style="color:var(--danger);">삭제</button>
                            </form>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </div>
</c:if>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
