<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="서류 보관함 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>서류 보관함</h1>
<p class="muted">프로젝트 산출물과 증빙 서류를 한곳에 모아둡니다. 프로젝트에 연결해 두면 어느 프로젝트의 서류인지 한눈에 볼 수 있습니다.</p>

<c:if test="${not empty documentsMessage}">
    <p style="background:var(--teal-bg); color:var(--teal); border:1px solid rgba(31,122,108,0.3); border-radius:6px; padding:10px 14px; margin-bottom:14px;"><c:out value="${documentsMessage}" /></p>
</c:if>
<c:if test="${not empty documentsError}">
    <p class="error-message"><c:out value="${documentsError}" /></p>
</c:if>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="card">
            <h2>서류 올리기</h2>
            <form action="${pageContext.request.contextPath}/documents" method="post" enctype="multipart/form-data"
                  class="row" style="margin-top:10px; align-items:flex-end;">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="upload">
                <span style="flex:1;"><label>파일</label><input type="file" name="file" required></span>
                <span style="flex:1;"><label>연결할 프로젝트 (선택)</label>
                    <select name="projectId">
                        <option value="">연결 안 함</option>
                        <c:forEach var="p" items="${projects}">
                            <option value="${p.id}"><c:out value="${p.title}" /></option>
                        </c:forEach>
                    </select>
                </span>
                <button type="submit">올리기</button>
            </form>
            <p class="muted" style="font-size:0.8rem; margin:8px 0 0;"><c:out value="${allowedHint}" /></p>
        </div>

        <div class="card">
            <h2>내 서류 ${documents.size()}개</h2>
            <c:choose>
                <c:when test="${empty documents}">
                    <p class="muted" style="margin-top:10px;">아직 올린 서류가 없습니다. 위에서 파일을 올리거나, 로드맵에서 프로젝트·증빙을 제출하면 여기에 모입니다.</p>
                </c:when>
                <c:otherwise>
                    <table style="margin-top:10px;">
                        <tr><th>파일</th><th>용도</th><th>연결된 프로젝트</th><th>크기</th><th>올린 날</th><th>작업</th></tr>
                        <c:forEach var="d" items="${documents}">
                            <tr>
                                <td><c:out value="${d.originalName}" /></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${empty d.purpose}"><span class="muted">—</span></c:when>
                                        <c:otherwise><span class="chip chip-teal"><c:out value="${d.purpose}" /></span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <form action="${pageContext.request.contextPath}/documents" method="post" class="inline-form">
                                        <input type="hidden" name="_csrf" value="${csrfToken}">
                                        <input type="hidden" name="action" value="link">
                                        <input type="hidden" name="documentId" value="${d.id}">
                                        <select name="projectId" onchange="this.form.submit()" aria-label="연결 프로젝트">
                                            <option value="">연결 안 함</option>
                                            <c:forEach var="p" items="${projects}">
                                                <option value="${p.id}" ${p.id == d.projectId ? 'selected' : ''}><c:out value="${p.title}" /></option>
                                            </c:forEach>
                                        </select>
                                    </form>
                                </td>
                                <td>${d.sizeLabel}</td>
                                <td>${d.uploadedDate}</td>
                                <td>
                                    <a href="${pageContext.request.contextPath}/documents/${d.id}">내려받기</a> ·
                                    <form action="${pageContext.request.contextPath}/documents" method="post" class="inline-form delete-doc"
                                          data-name="<c:out value='${d.originalName}' />
                                        <input type="hidden" name="_csrf" value="${csrfToken}">" data-purpose="<c:out value='${d.purpose}' />">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="documentId" value="${d.id}">
                                        <button type="submit" class="link-button" style="color:var(--danger);">삭제</button>
                                    </form>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                    <p class="muted" style="font-size:0.8rem; margin:8px 0 0;">
                        서류를 지우면 파일이 완전히 삭제됩니다. 이력서·자소서 지정과 프로젝트 문서 체크리스트에서도 함께 빠지지만,
                        로드맵 단계의 완료 표시와 받은 점수는 그대로 남습니다.
                    </p>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<script>
    document.querySelectorAll('.delete-doc').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            var purpose = form.getAttribute('data-purpose');
            var msg = "'" + form.getAttribute('data-name') + "' 서류를 삭제할까요?\n삭제한 파일은 되돌릴 수 없습니다.";
            if (purpose === '이력서' || purpose === '자소서') {
                msg += '\n(내 프로필의 ' + purpose + ' 지정도 함께 풀립니다.)';
            }
            if (!confirm(msg)) {
                event.preventDefault();
            }
        });
    });
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
