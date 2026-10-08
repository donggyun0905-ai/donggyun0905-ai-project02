<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 데스크톱 캐릭터 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="companion" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<div class="card">
    <h2 style="margin-top:0;">데스크톱 캐릭터 새 버전 올리기</h2>
    <p class="muted">
        <code>desktop-companion\build-companion.ps1</code>로 만든 <code>SpecOdysseyCompanion-Setup.exe</code>를 올리면
        사이트 [내려받기]가 이 파일로 바뀌고, 설치된 캐릭터는 다음 확인 때 "새 버전이 나왔어요"를 띄워요.
        버전은 <code>desktop-companion/pom.xml</code>의 버전과 같게 적어 주세요. 최근 2개 버전만 보관해요.
    </p>
    <form action="${ctx}/admin/companion" method="post" enctype="multipart/form-data">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <p><label>버전 <input type="text" name="version" placeholder="예: 0.2.0" required style="max-width:160px;"></label></p>
        <p><label>바뀐 점 (첫 줄이 캐릭터 말풍선에 나와요)
            <textarea name="notes" maxlength="500" rows="3" placeholder="예: 연습장을 캐릭터에서도 쓸 수 있어요"></textarea></label></p>
        <p><label>설치 파일 <input type="file" name="file" accept=".exe" required></label></p>
        <button type="submit">올리기</button>
    </form>
</div>

<div class="card">
    <h2 style="margin-top:0;">올라가 있는 버전</h2>
    <c:choose>
        <c:when test="${empty releases}">
            <p class="muted">아직 올린 버전이 없어요. 이용자가 [내려받기]를 누르면 "아직 올라간 설치 파일이 없어요"가 나와요.</p>
        </c:when>
        <c:otherwise>
            <ul class="item-list">
                <c:forEach var="r" items="${releases}" varStatus="s">
                    <li>
                        <strong><c:out value="${r.version}" /></strong><c:if test="${s.first}"> <span class="chip chip-teal">최신 · 내려받기에 쓰임</span></c:if>
                        <span class="muted"> · <c:out value="${r.sizeText}" /> · SHA-256 <code style="font-size:0.78rem;"><c:out value="${r.sha256}" /></code></span>
                        <c:if test="${not empty r.notes}"><div><c:out value="${r.notes}" /></div></c:if>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
