<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="공유받은 이력 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>공유받은 이력</h1>
<p class="muted">지원자가 보내 준 공유 링크를 담아 두는 곳입니다. 지원자가 공개한 항목만 보이고, 지원자가 공유를 멈추거나 링크가 만료되면 더 이상 열리지 않습니다.</p>

<div class="card" style="margin-top:16px;">
    <h2>지원자 담기</h2>
    <c:if test="${not empty errorMessage}">
        <p class="error-message" style="margin-top:10px;"><c:out value="${errorMessage}" /></p>
    </c:if>
    <form method="post" action="${pageContext.request.contextPath}/interviewer/shared" class="row" style="margin-top:10px;">
        <input type="hidden" name="action" value="add">
        <input type="text" name="link" required placeholder="받은 공유 링크 주소를 붙여 넣으세요" style="flex:1;">
        <button type="submit">담기</button>
    </form>
</div>

<div class="card">
    <div class="spread">
        <h2>담아 둔 지원자</h2>
        <c:if test="${not empty compare.applicants}">
            <a class="btn secondary" href="${pageContext.request.contextPath}/interviewer/compare">나란히 비교하기</a>
        </c:if>
    </div>
    <c:choose>
        <c:when test="${empty compare.applicants}">
            <p class="muted" style="margin-top:10px;">아직 담은 지원자가 없습니다. 지원자에게 받은 공유 링크를 위에 붙여 넣어 보세요.</p>
        </c:when>
        <c:otherwise>
            <ul class="item-list" style="margin-top:10px;">
                <c:forEach var="applicant" items="${compare.applicants}">
                    <li>
                        <div class="spread">
                            <strong><c:out value="${applicant.label}" /></strong>
                            <c:choose>
                                <c:when test="${applicant.available}"><span class="chip chip-teal">열람 가능</span></c:when>
                                <c:otherwise><span class="chip chip-locked">공유 중단됨</span></c:otherwise>
                            </c:choose>
                        </div>
                        <p class="muted" style="margin:6px 0; font-size:0.84rem;">
                            ${applicant.addedDate}에 담음
                            <c:choose>
                                <c:when test="${not applicant.available}"> · 지원자가 공유를 멈췄거나 링크가 만료되어 지금은 볼 수 없습니다</c:when>
                                <c:when test="${applicant.view.scopeBasic}">
                                    · <c:out value="${applicant.view.major}" default="전공 미입력" /> <c:out value="${applicant.view.grade}" />
                                    · 희망 직무 <c:out value="${applicant.view.desiredJobName}" default="미정" />
                                </c:when>
                                <c:otherwise> · 기본 이력은 공개하지 않음</c:otherwise>
                            </c:choose>
                        </p>
                        <div class="row">
                            <c:if test="${applicant.available}">
                                <a href="${pageContext.request.contextPath}/share/${applicant.token}">이력 보기</a>
                                <c:if test="${not empty applicant.view.resumeFileName}">
                                    <a href="${pageContext.request.contextPath}/share/${applicant.token}/resume">📎 이력서 내려받기</a>
                                </c:if>
                                <c:if test="${not empty applicant.view.coverLetterFileName}">
                                    <a href="${pageContext.request.contextPath}/share/${applicant.token}/cover-letter">📎 자소서 내려받기</a>
                                </c:if>
                            </c:if>
                            <form method="post" action="${pageContext.request.contextPath}/interviewer/shared" class="inline-form remove-item">
                                <input type="hidden" name="action" value="remove">
                                <input type="hidden" name="itemId" value="${applicant.itemId}">
                                <button type="submit" class="link-button">목록에서 빼기</button>
                            </form>
                        </div>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</div>

<script>
    document.querySelectorAll('.remove-item').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!confirm('이 지원자를 목록에서 뺄까요? 링크를 다시 붙여 넣으면 다시 담을 수 있습니다.')) {
                event.preventDefault();
            }
        });
    });
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
