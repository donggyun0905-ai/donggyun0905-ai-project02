<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 화면설계 PDF 공통 위젯 "오늘의 트렌드 기술" — FR-54·55.
     데이터(trendTechs)는 TrendWidgetFilter가 사용자의 목표 직무 기준으로 요청에 실어 준다.
     쓰는 쪽에서 <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" /> 로 오른쪽 칼럼에 넣는다. --%>
<div class="card">
    <h2 style="font-size:1rem;">오늘의 트렌드 기술</h2>
    <c:choose>
        <c:when test="${trendFallback}">
            <p class="muted" style="margin-top:4px;">목표 직무와 연결된 기술이 아직 없어 최근 트렌드를 보여줍니다</p>
        </c:when>
        <c:otherwise>
            <p class="muted" style="margin-top:4px;">관심 분야와 관련된 기술만 골라 보여줍니다</p>
        </c:otherwise>
    </c:choose>
    <c:choose>
        <c:when test="${empty trendTechs}">
            <p class="muted" style="margin-top:14px;">아직 표시할 트렌드 기술이 없습니다. 매일 0시에 새로 갱신됩니다.</p>
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
