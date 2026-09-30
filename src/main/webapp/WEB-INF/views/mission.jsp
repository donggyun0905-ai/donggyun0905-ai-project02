<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="오늘의 미션 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>오늘의 미션</h1>
<p class="muted"><c:if test="${not empty missionDateLabel}"><c:out value="${missionDateLabel}" /> · </c:if>매일 코딩테스트 문제 3개를 풀며 여정을 이어갑니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="align-items:stretch;">
            <%-- FR-53 연속 수행 — missionStreak(MissionProblemFilter)는 그날 배정된 문제를 모두 끝낸 날(제출·실패)을 센다 --%>
            <div class="card" style="flex:1;">
                <c:choose>
                    <c:when test="${not empty missionStreak}">
                        <h2>연속 <c:out value="${missionStreak.streak}" />일째</h2>
                        <p class="muted" style="margin-top:6px;">
                            <c:choose>
                                <c:when test="${missionStreak.todayDone}">오늘 미션 완료! 내일도 이어가세요</c:when>
                                <c:otherwise>오늘 미션을 끝내면 <c:out value="${missionStreak.nextStreak}" />일이 됩니다</c:otherwise>
                            </c:choose>
                        </p>
                        <div class="row" style="margin-top:10px; gap:6px;">
                            <c:forEach var="d" items="${missionStreak.days}">
                                <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;${d.today ? ' background:var(--gold); color:#fff; border-color:var(--gold);' : ''}"><c:out value="${d.dayOfWeek}" /> <c:out value="${d.dayOfMonth}" /><br><c:choose><c:when test="${d.done}">완료</c:when><c:when test="${d.today}">오늘</c:when><c:otherwise>쉼</c:otherwise></c:choose></div>
                            </c:forEach>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <h2>연속 수행</h2>
                        <p class="muted" style="margin-top:6px;">오늘 미션 3문제를 모두 끝내면 연속 기록이 쌓입니다</p>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="card" style="flex:1;">
                <h2>문제 난이도</h2>
                <%-- 등급·난이도·점수는 MissionProblemFilter가 넣어 주는 dailyMissionToday(사용자별) 값. 조회에 실패하면 일반 안내만 --%>
                <c:choose>
                    <c:when test="${not empty dailyMissionToday}">
                        <p style="margin-top:8px;">현재 등급 <strong><c:out value="${dailyMissionToday.tierName}" /></strong>에 맞춰 <strong>Lv<c:out value="${dailyMissionToday.levelMin}" />~<c:out value="${dailyMissionToday.levelMax}" /></strong> 문제가 나옵니다.</p>
                        <p class="muted" style="font-size:0.82rem;">등급이 오르면 더 높은 난이도가 섞여 나옵니다. 점수: 문제 풀이 +<c:out value="${dailyMissionToday.solvePoints}" />(현재 등급 기준)</p>
                    </c:when>
                    <c:otherwise>
                        <p style="margin-top:8px;">현재 등급에 맞는 난이도의 문제가 나옵니다.</p>
                        <p class="muted" style="font-size:0.82rem;">등급이 오르면 더 높은 난이도가 섞여 나옵니다.</p>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <div class="card">
            <%-- 데이터(dailyMissions)는 MissionProblemFilter가 사용자 등급·목표 직무에 맞춰 배정해 실어 준다 (FR-51·52).
                 지문은 저장하지 않는 링크 추천형이라 풀이는 원본 사이트에서 한다.
                 "정답 입력하기"는 MissionSubmitServlet(/mission/submit), "실패"는 MissionFailServlet(/mission/fail)로 간다. --%>
            <div class="spread"><h2>오늘의 문제 3개</h2><span class="muted"><c:out value="${empty dailyMissionDone ? 0 : dailyMissionDone}" /> / 3 완료</span></div>
            <c:choose>
                <c:when test="${empty dailyMissions}">
                    <p class="muted" style="margin-top:12px;">아직 추천할 문제가 없습니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="item-list" style="margin-top:12px;">
                        <c:forEach var="m" items="${dailyMissions}">
                            <%-- 실패는 완료(초록)와 구분되게 빨간 배경. 공통 CSS(A 담당)는 건드리지 않고 기존 색 변수만 쓴다.
                                 correct는 Boolean(NULL 가능) — NULL을 false로 읽지 않도록 null 여부를 먼저 본다 --%>
                            <c:set var="failed" value="${m.correct != null and not m.correct}" />
                            <li class="${m.completed and not failed ? 'completed' : ''}"
                                <c:if test="${failed}">style="border-color:var(--danger); background:var(--danger-bg);"</c:if>>
                                <div class="spread">
                                    <strong><c:out value="${m.title}" /></strong>
                                    <c:choose>
                                        <c:when test="${failed}"><span class="chip chip-danger">✘ 실패</span></c:when>
                                        <c:when test="${m.completed}"><span class="chip chip-teal">✔ 완료</span></c:when>
                                        <c:otherwise><span class="chip chip-gold">남음</span></c:otherwise>
                                    </c:choose>
                                </div>
                                <p class="muted" style="margin:6px 0;">링크 추천 · <c:out value="${m.sourceLabel}" /> · Lv<c:out value="${m.difficultyLevel}" /></p>
                                <div class="row">
                                    <a class="btn" href="<c:out value='${m.externalUrl}' />" target="_blank" rel="noopener noreferrer">풀러 가기</a>
                                    <a class="btn secondary" href="<c:url value='/mission/submit'><c:param name='missionId' value='${m.missionId}' /></c:url>">정답 입력하기</a>
                                    <form method="post" action="<c:url value='/mission/fail' />" style="display:inline;"
                                          onsubmit="return confirm('이 문제를 실패로 처리할까요?');">
                                        <input type="hidden" name="missionId" value="<c:out value='${m.missionId}' />">
                                        <button type="submit" class="secondary">실패</button>
                                    </form>
                                </div>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
