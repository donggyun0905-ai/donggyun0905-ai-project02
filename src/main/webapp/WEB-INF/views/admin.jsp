<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 점수·주기 규칙 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="rules" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>🛠 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<p class="muted">로드맵·복습·일일 문제의 점수와 주기를 코드 수정 없이 바꿉니다. 저장하면 새로 받는 점수부터 적용되고, 이미 쌓인 점수는 그대로입니다.
    숫자만 넣을 수 있고, 감쇠·연속 보너스를 뺀 값은 0을 쓸 수 없습니다. (기본값과 다른 값은 표시됩니다.)</p>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<form method="post" action="${pageContext.request.contextPath}/admin">
    <input type="hidden" name="_csrf" value="${csrfToken}">
    <c:forEach var="group" items="${ruleGroups}">
        <div class="card">
            <h2><c:out value="${group.key}" /></h2>
            <table style="margin-top:10px; width:100%;">
                <tr><th style="text-align:left;">설명</th><th>규칙 이름</th><th>값</th><th>기본값</th></tr>
                <c:forEach var="rule" items="${group.value}">
                    <tr>
                        <td><c:out value="${rule.description}" /></td>
                        <td><code><c:out value="${rule.key}" /></code></td>
                        <td style="text-align:center;">
                            <input type="number" name="rule_${rule.key}" value="${rule.value}" min="0" max="100000"
                                   style="width:96px;${rule.changed ? ' border-color:var(--gold); background:var(--teal-bg);' : ''}" required>
                        </td>
                        <td style="text-align:center;" class="muted"><c:out value="${rule.defaultValue}" /><c:if test="${rule.changed}"> ✎</c:if></td>
                    </tr>
                </c:forEach>
            </table>
        </div>
    </c:forEach>
    <button type="submit">저장</button>
</form>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
