<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 왼쪽 고정 위젯(2026-10-01 로드맵, 2026-10-02 자소서 첨삭·프로필을 뺀 모든 화면으로 확대) — 위: 일일 미션, 가운데: 최근 서류 보관함(토글), 아래: 한눈에 보기(D-day·지금 할 일·다음 등급).
     데이터(dailyMissions·dailyMissionDone·recentDocuments·glance)는 SideWidgetFilter(미션 화면은 MissionProblemFilter)가 실어 준다. --%>
<div class="card">
    <h2 style="font-size:1rem;">일일 미션</h2>
    <p style="margin:8px 0 6px; font-size:0.9rem;"><strong><c:out value="${dailyMissionDone}" default="0" /></strong> / <c:out value="${empty dailyMissions ? 0 : dailyMissions.size()}" /> 완료</p>
    <div class="progress-track"><div class="progress-fill teal" style="width:${empty dailyMissions or dailyMissions.size() == 0 ? 0 : (dailyMissionDone * 100) / dailyMissions.size()}%;"></div></div>
    <c:choose>
        <c:when test="${empty dailyMissions}">
            <p class="muted" style="margin:10px 0; font-size:0.85rem;">추천할 문제가 없습니다.</p>
        </c:when>
        <c:otherwise>
            <ul style="margin:10px 0; padding-left:18px; font-size:0.85rem;">
                <c:forEach var="m" items="${dailyMissions}">
                    <li>
                        <c:out value="${m.title}" /> ·
                        <c:choose>
                            <c:when test="${m.correct != null and not m.correct}">실패</c:when>
                            <c:when test="${m.completed}">완료</c:when>
                            <c:otherwise>남음</c:otherwise>
                        </c:choose>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
    <a href="${pageContext.request.contextPath}/mission" style="font-size:0.85rem;">미션 하러 가기</a>
</div>

<div class="card">
    <details>
        <summary style="font-weight:700; cursor:pointer;">최근 서류 보관함</summary>
        <c:choose>
            <c:when test="${empty recentDocuments}">
                <p class="muted" style="margin:10px 0 0; font-size:0.85rem;">보관된 서류가 없습니다.</p>
            </c:when>
            <c:otherwise>
                <ul style="list-style:none; margin:10px 0 0; padding:0; font-size:0.85rem; display:flex; flex-direction:column; gap:6px;">
                    <c:forEach var="d" items="${recentDocuments}">
                        <li><a href="${pageContext.request.contextPath}/documents/${d.id}"><span class="ic ic-file-text" aria-hidden="true"></span> <c:out value="${d.originalName}" /></a></li>
                    </c:forEach>
                </ul>
            </c:otherwise>
        </c:choose>
        <a href="${pageContext.request.contextPath}/documents" style="display:inline-block; margin-top:10px; font-size:0.85rem;">서류 보관함 전체 보기</a>
    </details>
</div>

<%-- 한눈에 보기 — 다가오는 D-day · 로드맵의 지금 할 일 · 다음 등급까지 (GlanceService, 2026-10-05) --%>
<c:if test="${not empty glance}">
<div class="card glance">
    <h2 style="font-size:1rem;">한눈에 보기</h2>

    <div class="glance-row">
        <div class="glance-label"><span class="ic ic-calendar" aria-hidden="true"></span> 다가오는 일정</div>
        <c:choose>
            <c:when test="${empty glance.ddays}">
                <p class="muted glance-empty">등록된 일정이 없습니다.</p>
            </c:when>
            <c:otherwise>
                <ul class="glance-ddays">
                    <c:forEach var="d" items="${glance.ddays}">
                        <li><span class="chip ${d.daysLeft <= 3 ? 'chip-danger' : 'chip-gold'}"><c:choose><c:when test="${d.daysLeft == 0}">D-DAY</c:when><c:otherwise>D-${d.daysLeft}</c:otherwise></c:choose></span><span class="glance-ddays-title"><c:out value="${d.title}" /></span></li>
                    </c:forEach>
                </ul>
            </c:otherwise>
        </c:choose>
        <a href="${pageContext.request.contextPath}/dday" class="glance-link">일정 관리</a>
    </div>

    <div class="glance-row">
        <div class="glance-label"><span class="ic ic-anchor" aria-hidden="true"></span> 지금 할 일</div>
        <c:choose>
            <c:when test="${empty glance.nextStepText}">
                <p class="muted glance-empty">진행 중인 로드맵 단계가 없습니다.</p>
            </c:when>
            <c:otherwise>
                <p class="glance-step"><span class="chip chip-teal"><c:out value="${glance.nextStepType}" /></span><c:out value="${glance.nextStepText}" /></p>
            </c:otherwise>
        </c:choose>
        <a href="${pageContext.request.contextPath}/roadmap" class="glance-link">로드맵으로</a>
    </div>

    <div class="glance-row">
        <div class="glance-label"><span class="ic ic-trending-up" aria-hidden="true"></span> 나의 등급</div>
        <p class="glance-tier"><strong><c:out value="${glance.tierName}" /></strong> <span class="muted">(<c:out value="${glance.tierTitle}" />) · <c:out value="${glance.totalScore}" />점</span></p>
        <div class="progress-track" style="margin:6px 0 4px;"><div class="progress-fill" style="width:${glance.tierPercent}%;"></div></div>
        <p class="muted glance-empty">
            <c:choose>
                <c:when test="${not empty glance.nextTierName}">다음 등급 '<c:out value="${glance.nextTierName}" />'까지 <c:out value="${glance.pointsToNextTier}" />점</c:when>
                <c:otherwise>최고 등급입니다</c:otherwise>
            </c:choose>
        </p>
    </div>
</div>
</c:if>
