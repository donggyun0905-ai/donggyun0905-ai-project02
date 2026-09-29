<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로드맵 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>⛵ 내 로드맵</h1>

<c:if test="${not empty errorMessage}">
    <p class="error-message">${errorMessage}</p>
</c:if>

<c:choose>
    <c:when test="${empty roadmap}">
        <div class="card">
            <p>아직 생성된 로드맵이 없습니다. 먼저 격차 분석을 완료해야 만들 수 있습니다.</p>
            <form action="${pageContext.request.contextPath}/roadmap" method="post">
                <input type="hidden" name="action" value="generate">
                <button type="submit">로드맵 생성하기</button>
            </form>
        </div>
    </c:when>
    <c:otherwise>
        <p class="muted">버전 ${roadmap.version} · 목표 수준 ${roadmap.targetLevel}</p>

        <p>진행도(현재 티어 기준): <strong>${progress.entryDone}/${progress.entryTotal}</strong> 완료 (${progress.entryPercent}%)</p>
        <div class="progress-track">
            <div class="progress-fill" style="width:${progress.entryPercent}%;"></div>
        </div>

        <h2>지금 할 일<c:if test="${progress.entryComplete}"> (ENTRY + CORE)</c:if><c:if test="${not progress.entryComplete}"> (ENTRY)</c:if></h2>
        <ul class="item-list">
            <c:forEach var="step" items="${steps}">
                <c:if test="${step.tier == 'ENTRY' || (step.tier == 'CORE' && progress.entryComplete)}">
                    <li class="${step.completed ? 'completed' : ''}">
                        <span class="chip ${step.tier == 'ENTRY' ? 'chip-entry' : 'chip-core'}">${step.tier}</span>
                        <span class="chip chip-type">${step.stepType}</span>
                        <c:if test="${step.completed}"><span class="done-mark">✅</span></c:if>
                        <p>${step.reason}</p>
                        <c:choose>
                            <c:when test="${step.stepType == 'PROJECT'}">
                                <c:choose>
                                    <c:when test="${step.completed}">
                                        완료 — <a href="${pageContext.request.contextPath}/profile">프로필에서 확인</a>
                                    </c:when>
                                    <c:otherwise>
                                        <details>
                                            <summary>프로젝트 등록하고 완료하기</summary>
                                            <form action="${pageContext.request.contextPath}/roadmap" method="post"
                                                  enctype="multipart/form-data">
                                                <input type="hidden" name="action" value="completeProject">
                                                <input type="hidden" name="stepId" value="${step.id}">
                                                <p><input type="text" name="title" placeholder="프로젝트명" required></p>
                                                <p><textarea name="description" placeholder="설명 (무엇을 했는지)" required></textarea></p>
                                                <p><input type="text" name="techStack" placeholder="사용 기술 (예: Java, Spring, MySQL)" required></p>
                                                <p class="row">
                                                    시작일 <input type="date" name="startDate">
                                                    종료일 <input type="date" name="endDate">
                                                </p>
                                                <p>
                                                    <label>증빙 파일(코드 캡처, 결과물 등 — 여러 개 가능, 필수)
                                                        <input type="file" name="files" multiple required>
                                                    </label>
                                                </p>
                                                <button type="submit">등록하고 완료하기</button>
                                            </form>
                                        </details>
                                    </c:otherwise>
                                </c:choose>
                            </c:when>
                            <c:otherwise>
                                <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                    <input type="hidden" name="action" value="complete">
                                    <input type="hidden" name="stepId" value="${step.id}">
                                    <c:choose>
                                        <c:when test="${step.completed}">
                                            <input type="hidden" name="completed" value="false">
                                            <button type="submit" class="link-button">완료 취소</button>
                                        </c:when>
                                        <c:otherwise>
                                            <input type="hidden" name="completed" value="true">
                                            <button type="submit">완료 체크</button>
                                        </c:otherwise>
                                    </c:choose>
                                </form>
                            </c:otherwise>
                        </c:choose>
                    </li>
                </c:if>
            </c:forEach>
        </ul>

        <c:if test="${not progress.entryComplete}">
            <c:set var="hasCoreSteps" value="false" />
            <c:forEach var="step" items="${steps}">
                <c:if test="${step.tier == 'CORE'}"><c:set var="hasCoreSteps" value="true" /></c:if>
            </c:forEach>
            <c:if test="${hasCoreSteps}">
                <h2>다음 단계 미리보기 (CORE)</h2>
                <p class="muted">지금 할 일(ENTRY)을 다 끝내면 완료 체크를 할 수 있게 풀립니다.</p>
                <ul class="item-list locked">
                    <c:forEach var="step" items="${steps}">
                        <c:if test="${step.tier == 'CORE'}">
                            <li><span class="chip chip-type">${step.stepType}</span> ${step.reason}</li>
                        </c:if>
                    </c:forEach>
                </ul>
            </c:if>
        </c:if>

        <form action="${pageContext.request.contextPath}/roadmap" method="post">
            <input type="hidden" name="action" value="generate">
            <button type="submit">다시 생성 (재분석 반영)</button>
        </form>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
