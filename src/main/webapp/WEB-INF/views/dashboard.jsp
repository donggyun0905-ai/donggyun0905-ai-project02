<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="대시보드 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:if test="${not empty upcomingDdays}">
    <c:set var="soonest" value="${upcomingDdays[0]}" />
    <div class="banner">
        <span>⏱️ 마감 임박 <strong>D-<c:out value="${soonest.daysLeft}" /></strong> <c:out value="${soonest.title}" /></span>
        <a href="${pageContext.request.contextPath}/dday">일정 보기</a>
    </div>
</c:if>

<h1>대시보드</h1>
<p class="muted">
    목표 직무 <strong><c:out value="${empty desiredJobName ? '미설정' : desiredJobName}" /></strong> ·
    <c:choose>
        <c:when test="${not empty currentTier}">
            지금은
            <c:choose>
                <c:when test="${currentTier.tier == 'ENTRY'}">입문(ENTRY)</c:when>
                <c:when test="${currentTier.tier == 'CORE'}">핵심(CORE)</c:when>
                <c:when test="${currentTier.tier == 'ADVANCED'}">심화(ADVANCED)</c:when>
                <c:otherwise>전문가(EXPERT)</c:otherwise>
            </c:choose>
            단계를 걷고 있습니다.
        </c:when>
        <c:otherwise>아직 로드맵이 없습니다. <a href="${pageContext.request.contextPath}/roadmap">로드맵 만들러 가기</a></c:otherwise>
    </c:choose>
</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="align-items:stretch;">
            <div class="card" style="flex:1;">
                <h2>내 스펙</h2>
                <div class="row" style="margin-top:12px; align-items:center; gap:18px;">
                    <div style="font-size:0.9rem; line-height:2;">
                        자격증 <strong><c:out value="${certCount}" />개</strong> ·
                        프로젝트 <strong><c:out value="${projectCount}" />개</strong><br>
                        보유 기술 <strong><c:out value="${skillCount}" />개</strong>
                    </div>
                </div>
                <p class="muted" style="font-size:0.78rem; margin-top:8px;">종합 완성도 점수는 집계 기능이 아직 없습니다.</p>
            </div>
            <div class="card" style="flex:1;">
                <h2>나의 등급</h2>
                <div class="row" style="margin-top:10px; gap:14px;">
                    <img src="${pageContext.request.contextPath}${empty tierLogoPath ? '/image/tier-3-practitioner.png' : tierLogoPath}" height="52">
                    <div>
                        <strong style="font-size:1.1rem;"><c:out value="${empty currentScoreTier.tierName ? '비기너' : currentScoreTier.tierName}" /></strong>
                        <div class="muted" style="font-size:0.8rem;"><c:out value="${currentScoreTier.titleName}" /> · 누적 <c:out value="${totalScore}" />점</div>
                    </div>
                </div>
                <div class="progress-track" style="margin-top:10px;"><div class="progress-fill" style="width:${tierProgressPercent}%;"></div></div>
                <p class="muted" style="font-size:0.8rem; margin:0;">
                    <c:choose>
                        <c:when test="${not empty nextTierName}">다음 등급 '<c:out value="${nextTierName}" />(<c:out value="${nextTierTitle}" />)'까지 <c:out value="${pointsToNextTier}" />점 · 미션 연속 <c:out value="${streakCount}" />일째</c:when>
                        <c:otherwise>최고 등급입니다 · 미션 연속 <c:out value="${streakCount}" />일째</c:otherwise>
                    </c:choose>
                </p>
            </div>
        </div>

        <div class="card">
            <h2>여정 지도</h2>
            <c:choose>
                <c:when test="${not empty journeyProgress}">
                    <div class="row" style="margin-top:14px; justify-content:space-between;">
                        <c:forEach var="t" items="${journeyProgress.tiers}">
                            <c:set var="tierLabel" value="${t.tier == 'ENTRY' ? '입문 ENTRY' : t.tier == 'CORE' ? '핵심 CORE' : t.tier == 'ADVANCED' ? '심화 ADVANCED' : '전문가 EXPERT'}" />
                            <div style="text-align:center;">
                                <div class="chip ${t.emptyTier ? 'chip-locked' : !t.unlocked ? 'chip-locked' : t.complete ? 'chip-teal' : 'chip-gold'}">
                                    <c:choose>
                                        <c:when test="${t.emptyTier || !t.unlocked}">○</c:when>
                                        <c:when test="${t.complete}">✔</c:when>
                                        <c:otherwise>⛵</c:otherwise>
                                    </c:choose>
                                </div>
                                <div style="margin-top:6px; font-weight:600;"><c:out value="${tierLabel}" /></div>
                                <div class="muted" style="font-size:0.78rem;">
                                    <c:choose>
                                        <c:when test="${t.emptyTier}">해당 없음</c:when>
                                        <c:when test="${!t.unlocked}">다음 목적지</c:when>
                                        <c:when test="${t.complete}">완료</c:when>
                                        <c:otherwise>지금 여기</c:otherwise>
                                    </c:choose>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                    <p style="margin-top:16px; margin-bottom:0;">
                        <c:choose>
                            <c:when test="${not empty nextStepReason}"><strong>지금 할 일</strong> <c:out value="${nextStepReason}" /> · </c:when>
                        </c:choose>
                        <a href="${pageContext.request.contextPath}/roadmap">로드맵에서 보기</a>
                    </p>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="margin-top:10px;">아직 만든 로드맵이 없습니다. <a href="${pageContext.request.contextPath}/roadmap">로드맵 만들러 가기</a></p>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="row" style="align-items:stretch;">
            <div class="card" style="flex:1;">
                <h2>오늘의 미션</h2>
                <p style="margin:10px 0 6px;"><strong><c:out value="${dailyMissionDone}" /></strong> / <c:out value="${empty dailyMissions ? 0 : dailyMissions.size()}" /> 완료</p>
                <div class="progress-track"><div class="progress-fill teal" style="width:${dailyMissionPercent}%;"></div></div>
                <c:choose>
                    <c:when test="${empty dailyMissions}">
                        <p class="muted" style="margin:10px 0; font-size:0.85rem;">추천할 문제가 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <ul style="margin:10px 0; padding-left:18px; font-size:0.88rem;">
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
                <a href="${pageContext.request.contextPath}/mission">미션 하러 가기</a>
            </div>
            <div class="card" style="flex:1;">
                <h2>다가오는 일정</h2>
                <c:choose>
                    <c:when test="${empty upcomingDdays}">
                        <p class="muted" style="margin:10px 0; font-size:0.88rem;">등록된 일정이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <ul style="list-style:none; margin:10px 0; padding:0; font-size:0.88rem; display:flex; flex-direction:column; gap:6px;">
                            <c:forEach var="d" items="${upcomingDdays}">
                                <li><span class="chip ${d.daysLeft <= 3 ? 'chip-danger' : 'chip-gold'}">D-<c:out value="${d.daysLeft}" /></span> <c:out value="${d.title}" /></li>
                            </c:forEach>
                        </ul>
                    </c:otherwise>
                </c:choose>
                <a href="${pageContext.request.contextPath}/dday">전체 일정 보기</a>
            </div>
        </div>

        <div class="card spread">
            <div>
                <c:choose>
                    <c:when test="${not hasGapAnalysis}">
                        <h2>격차 분석 결과 없음</h2>
                        <p class="muted" style="margin-top:6px;">아직 격차 분석을 진행하지 않았습니다.</p>
                    </c:when>
                    <c:when test="${empty missingSkillNames}">
                        <h2>부족한 필수 역량 없음</h2>
                        <p class="muted" style="margin-top:6px;">요구 기술 <c:out value="${gapTotalCount}" />개를 모두 충족했습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <h2>부족한 필수 역량 <c:out value="${missingSkillNames.size()}" />개</h2>
                        <p class="muted" style="margin-top:6px;">
                            <c:forEach var="n" items="${missingSkillNames}" varStatus="st"><c:out value="${n}" />${st.last ? '' : ', '}</c:forEach>
                            · 요구 기술 <c:out value="${gapTotalCount}" />개 중 <c:out value="${gapMetCount}" />개 충족
                        </p>
                    </c:otherwise>
                </c:choose>
            </div>
            <a class="btn" href="${pageContext.request.contextPath}/gap-analysis">격차 분석 보기</a>
        </div>
    </div>

    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
