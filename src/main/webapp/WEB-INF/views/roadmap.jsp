<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로드맵 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>내 로드맵</h1>

<c:if test="${not empty errorMessage}">
    <p style="color:red;">${errorMessage}</p>
</c:if>

<c:choose>
    <c:when test="${empty roadmap}">
        <p>아직 생성된 로드맵이 없습니다. 먼저 격차 분석을 완료해야 만들 수 있습니다.</p>
        <form action="${pageContext.request.contextPath}/roadmap" method="post">
            <input type="hidden" name="action" value="generate">
            <button type="submit">로드맵 생성하기</button>
        </form>
    </c:when>
    <c:otherwise>
        <p>버전 ${roadmap.version} · 목표 수준 ${roadmap.targetLevel}</p>

        <h2>지금 할 일 (ENTRY)</h2>
        <ol>
            <c:forEach var="step" items="${steps}">
                <c:if test="${step.tier == 'ENTRY'}">
                    <li>
                        <strong>[${step.stepType}]</strong> ${step.reason}
                        <form action="${pageContext.request.contextPath}/roadmap" method="post" style="display:inline;">
                            <input type="hidden" name="action" value="complete">
                            <input type="hidden" name="stepId" value="${step.id}">
                            <c:choose>
                                <c:when test="${step.completed}">
                                    <input type="hidden" name="completed" value="false">
                                    <button type="submit">완료 취소</button> ✅
                                </c:when>
                                <c:otherwise>
                                    <input type="hidden" name="completed" value="true">
                                    <button type="submit">완료 체크</button>
                                </c:otherwise>
                            </c:choose>
                        </form>
                    </li>
                </c:if>
            </c:forEach>
        </ol>

        <c:set var="hasCoreSteps" value="false" />
        <c:forEach var="step" items="${steps}">
            <c:if test="${step.tier == 'CORE'}"><c:set var="hasCoreSteps" value="true" /></c:if>
        </c:forEach>
        <c:if test="${hasCoreSteps}">
            <h2>다음 단계 미리보기 (CORE)</h2>
            <p>지금 할 일을 끝내면 이어서 진행할 것들입니다. 아직 완료 체크는 할 수 없습니다.</p>
            <ul style="color:gray;">
                <c:forEach var="step" items="${steps}">
                    <c:if test="${step.tier == 'CORE'}">
                        <li>[${step.stepType}] ${step.reason}</li>
                    </c:if>
                </c:forEach>
            </ul>
        </c:if>

        <form action="${pageContext.request.contextPath}/roadmap" method="post">
            <input type="hidden" name="action" value="generate">
            <button type="submit">다시 생성 (재분석 반영)</button>
        </form>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
