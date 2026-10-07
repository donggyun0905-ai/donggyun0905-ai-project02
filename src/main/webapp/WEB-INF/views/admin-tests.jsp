<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 테스트 목록 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<div class="card">
    <div class="spread">
        <h2>기능별 테스트</h2>
        <span class="pill">요구사항 ${summary.total}개 · 테스트 있음 ${summary.tested}개</span>
    </div>
    <p class="muted" style="margin-top:4px;">
        기능마다 어떤 테스트가 그 기능을 지키고 있는지 보여 줍니다. 테스트 주석에 적힌 요구사항 번호와,
        그 기능에 연결된 클래스 이름을 딴 테스트(<code>ProfileService</code> → <code>ProfileServiceTest</code>)를
        함께 찾아 만든 표입니다.
    </p>
    <c:if test="${summary.untested > 0}">
        <p class="muted" style="margin-top:4px;">테스트가 연결되지 않은 기능이 <strong>${summary.untested}개</strong> 있습니다 — 아래에서 <span class="chip" style="background:var(--danger-bg); color:var(--danger);">테스트 없음</span>으로 표시됩니다.</p>
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
                        <th style="text-align:left; width:60px;">개수</th>
                        <th style="text-align:left;">테스트</th>
                    </tr>
                    <c:forEach var="f" items="${area.value}">
                        <tr>
                            <td><strong><c:out value="${f.id}" /></strong></td>
                            <td><c:out value="${f.title}" /></td>
                            <td>
                                <c:choose>
                                    <c:when test="${f.tested}"><span class="chip chip-teal">${f.testCount}</span></c:when>
                                    <c:otherwise><span class="chip" style="background:var(--danger-bg); color:var(--danger);">0</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td class="muted" style="font-size:0.84rem;">
                                <c:choose>
                                    <c:when test="${f.tested}">
                                        <c:forEach var="t" items="${f.tests}" varStatus="s"><c:out value="${t}" /><c:if test="${!s.last}"> · </c:if></c:forEach>
                                    </c:when>
                                    <c:otherwise>
                                        <span style="color:var(--danger);">테스트 없음</span>
                                        <c:if test="${!f.implemented}"> (아직 구현도 되지 않은 기능입니다)</c:if>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                </table>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
