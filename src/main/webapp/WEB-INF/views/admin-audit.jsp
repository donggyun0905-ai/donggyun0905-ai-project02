<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 감사 로그 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="audit" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<div class="card">
    <div class="spread">
        <h2>감사 로그</h2>
        <span class="pill">전체 ${totalCount}건</span>
    </div>
    <p class="muted" style="margin-top:4px;">관리자 화면에서 남의 데이터를 바꾼 기록입니다. 고치거나 지울 수 없습니다.</p>

    <%-- 조회만 하는 GET 폼이라 CSRF 토큰이 필요 없다 --%>
    <form method="get" action="${ctx}/admin/audit" class="row" style="margin-top:10px; gap:8px;">
        <select name="action" style="flex:1; max-width:320px;">
            <option value="">모든 행동</option>
            <c:forEach var="one" items="${usedActions}">
                <option value="${one}" ${one == actionFilter ? 'selected' : ''}><c:out value="${actionLabels[one]}" /></option>
            </c:forEach>
        </select>
        <button type="submit">보기</button>
        <c:if test="${not empty actionFilter}">
            <a class="btn secondary" href="${ctx}/admin/audit">필터 해제</a>
        </c:if>
    </form>

    <c:choose>
        <c:when test="${empty logs}">
            <p class="muted" style="margin-top:14px;">
                <c:choose>
                    <c:when test="${not empty actionFilter}">이 행동으로 남은 기록이 없습니다.</c:when>
                    <c:otherwise>아직 남은 기록이 없습니다. 관리자 화면에서 무언가를 바꾸면 여기에 쌓입니다.</c:otherwise>
                </c:choose>
            </p>
        </c:when>
        <c:otherwise>
            <table style="margin-top:12px; width:100%; font-size:0.9rem;">
                <tr>
                    <th style="text-align:left; width:150px;">시각</th>
                    <th style="text-align:left; width:130px;">관리자</th>
                    <th style="text-align:left; width:160px;">행동</th>
                    <th style="text-align:left; width:200px;">대상</th>
                    <th style="text-align:left;">내용</th>
                </tr>
                <c:forEach var="log" items="${logs}">
                    <tr>
                        <td class="muted"><c:out value="${log.createdAtText}" /></td>
                        <td><c:out value="${log.adminLoginId}" /></td>
                        <td><span class="chip chip-teal"><c:out value="${log.actionLabel}" /></span></td>
                        <td class="muted">
                            <c:out value="${log.targetType}" /><c:if test="${not empty log.targetId}"> #${log.targetId}</c:if>
                        </td>
                        <td><c:out value="${log.detail}" default="—" /></td>
                    </tr>
                </c:forEach>
            </table>

            <c:if test="${totalPages > 1}">
                <div class="row" style="justify-content:center; gap:10px; margin-top:14px;">
                    <c:if test="${page > 1}">
                        <a class="btn secondary" href="${ctx}/admin/audit?page=${page - 1}<c:if test="${not empty actionFilter}">&action=${actionFilter}</c:if>">이전</a>
                    </c:if>
                    <span class="muted">${page} / ${totalPages}</span>
                    <c:if test="${page < totalPages}">
                        <a class="btn secondary" href="${ctx}/admin/audit?page=${page + 1}<c:if test="${not empty actionFilter}">&action=${actionFilter}</c:if>">다음</a>
                    </c:if>
                </div>
            </c:if>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
