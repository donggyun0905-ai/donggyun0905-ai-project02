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
        <input type="hidden" name="_csrf" value="${csrfToken}">
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
<%-- 검토 상태로 걸러 보기 — 면접관 본인의 기록이라 지원자에게는 보이지 않는다 --%>
<div class="row" style="gap:6px; flex-wrap:wrap; margin-top:10px;">
    <a class="chip ${empty statusFilter ? 'chip-teal' : 'chip-locked'}" href="${pageContext.request.contextPath}/interviewer/shared">전체 ${statusCounts['ALL']}</a>
    <c:forEach var="entry" items="${statusLabels}">
        <a class="chip ${statusFilter == entry.key ? 'chip-teal' : 'chip-locked'}" href="${pageContext.request.contextPath}/interviewer/shared?status=${entry.key}"><c:out value="${entry.value}" /> ${statusCounts[entry.key]}</a>
    </c:forEach>
</div>
    <c:choose>
        <c:when test="${empty compare.applicants and not empty statusFilter}">
            <p class="muted" style="margin-top:10px;">이 상태의 지원자가 없습니다.</p>
        </c:when>
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
                                    <a href="${pageContext.request.contextPath}/share/${applicant.token}/resume" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> 이력서 내려받기</a>
                                </c:if>
                                <c:if test="${not empty applicant.view.coverLetterFileName}">
                                    <a href="${pageContext.request.contextPath}/share/${applicant.token}/cover-letter" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> 자소서 내려받기</a>
                                </c:if>
                            </c:if>
                            <form method="post" action="${pageContext.request.contextPath}/interviewer/shared" class="inline-form remove-item">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="remove">
                                <input type="hidden" name="itemId" value="${applicant.itemId}">
                                <button type="submit" class="link-button">목록에서 빼기</button>
                            </form>
                        </div>
                        <%-- 내 검토 기록: 상태 · 평점 · 메모 (지원자에게는 보이지 않는다). 기본은 닫힘 — 메모가 있으면 요약 줄에 표시만 --%>
                        <details style="margin-top:8px;">
                            <summary style="cursor:pointer;">
                                내 검토 · <strong><c:out value="${applicant.reviewStatusLabel}" /></strong>
                                <c:if test="${not empty applicant.rating}"> · 평점 ${applicant.rating}/5</c:if>
                                <c:if test="${not empty applicant.memo}"> · <span class="muted">메모 있음</span></c:if>
                            </summary>
                            <form method="post" action="${pageContext.request.contextPath}/interviewer/shared" style="margin-top:8px;">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="evaluate">
                                <input type="hidden" name="itemId" value="${applicant.itemId}">
                                <c:if test="${not empty statusFilter}"><input type="hidden" name="status" value="${statusFilter}"></c:if>
                                <p class="row" style="gap:8px;">
                                    <span style="flex:1;"><label>상태</label>
                                        <select name="reviewStatus">
                                            <c:forEach var="entry" items="${statusLabels}">
                                                <option value="${entry.key}" ${applicant.reviewStatus == entry.key ? 'selected' : ''}><c:out value="${entry.value}" /></option>
                                            </c:forEach>
                                        </select>
                                    </span>
                                    <span style="flex:1;"><label>평점</label>
                                        <select name="rating">
                                            <option value="" ${empty applicant.rating ? 'selected' : ''}>미평가</option>
                                            <c:forEach var="score" begin="1" end="5">
                                                <option value="${score}" ${applicant.rating == score ? 'selected' : ''}>${score}점</option>
                                            </c:forEach>
                                        </select>
                                    </span>
                                </p>
                                <p><textarea name="memo" maxlength="1000" rows="3" placeholder="면접에서 물어볼 것, 인상 깊었던 점 등 (나만 보입니다)"><c:out value="${applicant.memo}" /></textarea></p>
                                <button type="submit" class="secondary">검토 저장</button>
                            </form>
                        </details>
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
