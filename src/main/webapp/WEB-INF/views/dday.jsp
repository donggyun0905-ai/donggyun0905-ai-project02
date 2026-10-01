<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="D-day 알림 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>D-day 알림</h1>
<p class="muted">자격증 접수일과 공채 마감일을 놓치지 않도록 모아둡니다. 챙길 일정을 직접 추가해 두면 마감이 가까운 순으로 보여줍니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <%-- FR-72 마감 임박 강조 --%>
        <c:if test="${not empty urgentItem}">
            <div class="banner">
                <span><strong style="font-size:1.3rem; color:var(--danger);">${urgentItem.ddayText}</strong> &nbsp;<c:out value="${urgentItem.title}" /></span>
                <span class="muted">${urgentItem.targetDate} · ${urgentItem.typeLabel}</span>
            </div>
        </c:if>

        <div class="card">
            <h2>일정 직접 추가</h2>
            <c:if test="${not empty errorMessage}">
                <p class="error-message"><c:out value="${errorMessage}" /></p>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/dday" class="row" style="margin-top:10px; align-items:flex-end;">
                <span style="flex:2;"><label for="title">제목</label><input type="text" id="title" name="title" maxlength="100" required placeholder="예) B사 인턴 서류 마감" value="<c:out value='${empty errorMessage ? null : param.title}' />"></span>
                <span style="flex:1;"><label for="targetDate">날짜</label><input type="date" id="targetDate" name="targetDate" min="${today}" required value="<c:out value='${empty errorMessage ? null : param.targetDate}' />"></span>
                <span style="flex:1;"><label for="alertType">종류</label>
                    <select id="alertType" name="alertType">
                        <option value="RECRUIT" ${param.alertType == 'RECRUIT' ? 'selected' : ''}>공채</option>
                        <option value="CERT" ${param.alertType == 'CERT' ? 'selected' : ''}>자격증</option>
                        <option value="CUSTOM" ${param.alertType == 'CUSTOM' ? 'selected' : ''}>기타</option>
                    </select>
                </span>
                <button type="submit">추가하기</button>
            </form>
        </div>

        <div class="card">
            <h2>다가오는 일정</h2>
            <c:if test="${not empty listErrorMessage}">
                <p class="error-message" style="margin-top:10px;"><c:out value="${listErrorMessage}" /></p>
            </c:if>
            <c:choose>
                <c:when test="${empty items}">
                    <p class="muted" style="margin-top:10px;">아직 등록된 일정이 없습니다. 위에서 첫 일정을 추가해 보세요.</p>
                </c:when>
                <c:otherwise>
                    <ul class="item-list" style="margin-top:10px;">
                        <c:forEach var="item" items="${items}">
                            <li class="spread">
                                <span>
                                    <strong><c:out value="${item.title}" /></strong><br>
                                    <span class="muted" style="font-size:0.82rem;">${item.targetDate} · ${item.typeLabel}</span>
                                </span>
                                <span class="row">
                                    <c:choose>
                                        <c:when test="${item.urgency == 'URGENT'}"><span class="chip chip-danger">${item.ddayText}</span></c:when>
                                        <c:when test="${item.urgency == 'UPCOMING'}"><span class="chip chip-gold">${item.ddayText}</span></c:when>
                                        <c:otherwise><span class="chip chip-locked">${item.ddayText}</span></c:otherwise>
                                    </c:choose>
                                    <details class="inline-form">
                                        <summary>수정</summary>
                                        <form method="post" action="${pageContext.request.contextPath}/dday">
                                            <input type="hidden" name="action" value="update">
                                            <input type="hidden" name="alertId" value="${item.id}">
                                            <p><input type="text" name="title" maxlength="100" required value="<c:out value='${item.title}' />"></p>
                                            <p><input type="date" name="targetDate" required value="${item.targetDate}"></p>
                                            <p>
                                                <select name="alertType">
                                                    <option value="RECRUIT" ${item.alertType == 'RECRUIT' ? 'selected' : ''}>공채</option>
                                                    <option value="CERT" ${item.alertType == 'CERT' ? 'selected' : ''}>자격증</option>
                                                    <option value="CUSTOM" ${item.alertType == 'CUSTOM' ? 'selected' : ''}>기타</option>
                                                </select>
                                            </p>
                                            <button type="submit">저장</button>
                                        </form>
                                    </details>
                                    <form method="post" action="${pageContext.request.contextPath}/dday" class="delete-item inline-form">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="alertId" value="${item.id}">
                                        <button type="submit" class="link-button">삭제</button>
                                    </form>
                                </span>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<script>
    document.querySelectorAll('.delete-item').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!confirm('이 일정을 삭제할까요?')) {
                event.preventDefault();
            }
        });
    });
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
