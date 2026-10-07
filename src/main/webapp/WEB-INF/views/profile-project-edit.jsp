<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty projectId ? '프로젝트 추가' : '프로젝트 수정'} - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><c:choose><c:when test="${empty projectId}">프로젝트 추가</c:when><c:otherwise>프로젝트 수정</c:otherwise></c:choose></h1>
<p class="muted">면접관에게 보이는 항목을 모두 여기서 적을 수 있습니다. 회고·기술 활용 설명서·서류는 모두 선택이고, 나중에 다시 와서 채워도 됩니다.</p>

<c:if test="${not empty errorMessage}">
    <p class="error-message"><c:out value="${errorMessage}" /></p>
</c:if>

<div class="card">
    <%-- 로드맵 PROJECT 단계 제출과 같은 입력칸을 쓴다 — 두 화면이 받는 항목이 갈라지지 않게 --%>
    <form action="${pageContext.request.contextPath}/profile/projects/edit" method="post" enctype="multipart/form-data">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <c:if test="${not empty projectId}"><input type="hidden" name="projectId" value="${projectId}"></c:if>
        <%@ include file="/WEB-INF/views/common/project-submit-fields.jsp" %>
        <p class="row" style="margin-top:12px;">
            <button type="submit">저장</button>
            <a class="btn secondary" href="${pageContext.request.contextPath}/profile">취소</a>
        </p>
    </form>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
