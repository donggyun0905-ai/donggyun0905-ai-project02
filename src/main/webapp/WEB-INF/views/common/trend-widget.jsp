<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 화면설계 PDF 공통 위젯 "오늘의 트렌드 기술" — FR-54·55.
     데이터(trendTechs·trendSource)는 SideWidgetFilter가 TrendWidgetService로 사용자의 목표 직무 기준에 맞춰 요청에 실어 준다.
     쓰는 쪽에서 <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" /> 로 오른쪽 칼럼에 넣는다. --%>
<div class="card">
    <h2 style="font-size:1rem;">오늘의 트렌드 기술</h2>
    <%-- trendSource는 SideWidgetFilter가 TrendWidgetService 결과로 실어 준다 — 어떤 기준으로 고른 목록인지 밝힌다 --%>
    <p class="muted" style="margin-top:4px;">
        <c:choose>
            <c:when test="${trendSource == 'GENERAL'}">목표 직무를 정하기 전이라 최근 트렌드를 보여줍니다</c:when>
            <c:when test="${trendSource == 'CATEGORY'}">목표 직무와 같은 계열 직무에서 뜨는 기술을 보여줍니다</c:when>
            <c:when test="${trendSource == 'JOB_AND_CATEGORY'}">목표 직무 트렌드에 같은 계열 직무의 트렌드를 더해 보여줍니다</c:when>
            <c:otherwise>목표 직무와 관련된 기술만 골라 보여줍니다</c:otherwise>
        </c:choose>
    </p>
    <c:choose>
        <c:when test="${empty trendTechs}">
            <p class="muted" style="margin-top:14px;">아직 목표 직무와 연결된 트렌드 기술이 없습니다. 매일 0시에 새로 모읍니다.</p>
        </c:when>
        <c:otherwise>
            <c:forEach var="tech" items="${trendTechs}" varStatus="st">
                <c:if test="${!st.first}">
                    <hr style="border:none; border-top:1px solid var(--border); margin:14px 0;">
                </c:if>
                <div style="${st.first ? 'margin-top:14px;' : ''}">
                    <div style="font-weight:700; font-size:0.92rem;"><c:out value="${tech.techName}" /></div>
                    <div class="muted" style="margin:4px 0;"><c:out value="${tech.summary}" /></div>
                    <a href="<c:out value='${tech.sourceUrl}' />" target="_blank" rel="noopener noreferrer"
                       style="font-size:0.82rem;">출처 보기</a>
                </div>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>
