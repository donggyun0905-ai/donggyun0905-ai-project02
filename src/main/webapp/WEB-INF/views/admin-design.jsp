<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 기능 설계서 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<div class="card">
    <div class="spread">
        <h2>기능 설계서</h2>
        <span class="pill">요구사항 ${summary.total}개 · 구현 ${summary.implemented}개</span>
    </div>
    <p class="muted" style="margin-top:4px;">
        요구사항 명세서의 기능 하나하나가 어떤 화면·서블릿·서비스·DAO로 만들어졌는지 보여 줍니다.
        손으로 쓴 문서가 아니라 <strong>코드 주석(<code>관련 요구사항: FR-xx</code>)을 읽어 빌드 때 만든 표</strong>라,
        코드가 바뀌면 이 화면도 함께 바뀝니다.
    </p>
    <c:if test="${summary.missing > 0}">
        <p class="muted" style="margin-top:4px;">아직 연결된 코드가 없는 기능이 <strong>${summary.missing}개</strong> 있습니다 — 아래에서 <span class="chip" style="background:var(--danger-bg); color:var(--danger);">미구현</span>으로 표시됩니다.</p>
    </c:if>

    <c:choose>
        <c:when test="${empty byArea}">
            <p class="muted" style="margin-top:14px;">연결표를 찾을 수 없습니다. <code>mvn test</code>를 한 번 돌려
                <code>src/main/resources/feature-coverage.json</code>을 만든 뒤 다시 배포해 주세요.</p>
        </c:when>
        <c:otherwise>
            <c:forEach var="area" items="${byArea}">
                <h3 style="margin:20px 0 6px; font-size:1rem;"><c:out value="${area.key}" /></h3>
                <table style="width:100%; font-size:0.88rem;">
                    <tr>
                        <th style="text-align:left; width:70px;">번호</th>
                        <th style="text-align:left; width:260px;">기능</th>
                        <th style="text-align:left;">만들어진 곳</th>
                    </tr>
                    <c:forEach var="f" items="${area.value}">
                        <tr>
                            <td><strong><c:out value="${f.id}" /></strong></td>
                            <td>
                                <c:out value="${f.title}" />
                                <c:choose>
                                    <c:when test="${f.implemented}"><span class="chip chip-teal">구현</span></c:when>
                                    <c:otherwise><span class="chip" style="background:var(--danger-bg); color:var(--danger);">미구현</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td class="muted" style="font-size:0.84rem;">
                                <c:if test="${not empty f.views}">
                                    <div><strong>화면</strong>
                                        <c:forEach var="v" items="${f.views}" varStatus="s"><c:out value="${v}" /><c:if test="${!s.last}"> · </c:if></c:forEach>
                                    </div>
                                </c:if>
                                <c:if test="${not empty f.controllers}">
                                    <div><strong>요청</strong>
                                        <c:forEach var="v" items="${f.controllers}" varStatus="s"><c:out value="${v}" /><c:if test="${!s.last}"> · </c:if></c:forEach>
                                    </div>
                                </c:if>
                                <c:if test="${not empty f.services}">
                                    <div><strong>로직</strong>
                                        <c:forEach var="v" items="${f.services}" varStatus="s"><c:out value="${v}" /><c:if test="${!s.last}"> · </c:if></c:forEach>
                                    </div>
                                </c:if>
                                <c:if test="${not empty f.daos}">
                                    <div><strong>DB</strong>
                                        <c:forEach var="v" items="${f.daos}" varStatus="s"><c:out value="${v}" /><c:if test="${!s.last}"> · </c:if></c:forEach>
                                    </div>
                                </c:if>
                                <c:if test="${!f.implemented}">아직 연결된 코드가 없습니다.</c:if>
                            </td>
                        </tr>
                    </c:forEach>
                </table>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
