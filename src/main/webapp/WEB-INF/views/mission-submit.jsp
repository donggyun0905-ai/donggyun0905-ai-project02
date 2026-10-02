<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="정답 입력하기 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<%-- FR-53 풀이 코드 제출. 데이터(mission, languages, selectedLanguage, code)는 MissionSubmitServlet이 넣어 준다.
     제출하면 서버에서 Judge0로 컴파일(문법)만 확인하고, 통과하면 저장 후 미션 화면으로 돌아간다. --%>
<h1>정답 입력하기</h1>
<p class="muted">푼 코드를 붙여 넣으면 컴파일되는지 확인한 뒤 저장합니다. 정답 채점은 하지 않습니다.</p>

<div class="card" style="margin-top:16px;">
    <div class="spread">
        <strong><c:out value="${mission.title}" /></strong>
        <c:choose>
            <c:when test="${mission.correct != null and not mission.correct}"><span class="chip chip-danger">✘ 실패</span></c:when>
            <c:when test="${mission.completed}"><span class="chip chip-teal">✔ 완료</span></c:when>
            <c:otherwise><span class="chip chip-gold">남음</span></c:otherwise>
        </c:choose>
    </div>
    <p class="muted" style="margin:6px 0;">Lv<c:out value="${mission.difficultyLevel}" /></p>
    <a class="btn secondary" href="<c:out value='${mission.externalUrl}' />" target="_blank" rel="noopener noreferrer">문제 보기</a>
</div>

<div class="card">
    <c:if test="${not empty errorMessage}">
        <p class="error-message"><c:out value="${errorMessage}" /></p>
    </c:if>
    <c:if test="${not empty compileError}">
        <div class="error-message">
            <strong>컴파일에 실패했습니다.</strong> 오류를 고친 뒤 다시 제출해 주세요.
            <pre style="margin:8px 0 0; white-space:pre-wrap; font-size:0.8rem;"><c:out value="${compileError}" /></pre>
        </div>
    </c:if>

    <form method="post" action="<c:url value='/mission/submit' />">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="missionId" value="<c:out value='${mission.missionId}' />">

        <label for="language">언어</label>
        <select id="language" name="language" style="max-width:220px;">
            <c:forEach var="lang" items="${languages}">
                <option value="${lang.code}" ${lang.code == selectedLanguage ? 'selected' : ''}><c:out value="${lang.label}" /></option>
            </c:forEach>
        </select>

        <label for="code" style="margin-top:12px;">풀이 코드</label>
        <textarea id="code" name="code" rows="18" spellcheck="false" required maxlength="50000"
                  style="font-family:Consolas, 'Courier New', monospace; font-size:0.85rem; tab-size:4;"><c:out value="${code}" /></textarea>

        <div class="row" style="margin-top:12px;">
            <button type="submit">제출하기</button>
            <a class="btn secondary" href="<c:url value='/mission' />">돌아가기</a>
        </div>
    </form>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
