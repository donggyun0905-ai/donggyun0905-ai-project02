<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 왼쪽 고정 위젯(2026-10-01 로드맵, 2026-10-02 자소서 첨삭·프로필을 뺀 모든 화면으로 확대) — 위: 일일 미션, 아래: 최근 서류 보관함(토글).
     데이터(dailyMissions·dailyMissionDone·recentDocuments)는 SideWidgetFilter(미션 화면은 MissionProblemFilter)가 실어 준다. --%>
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
