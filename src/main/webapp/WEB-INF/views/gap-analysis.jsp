<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="격차 분석 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:choose>
    <%-- 6-1. AI 응답 대기 --%>
    <c:when test="${state == 'loading'}">
        <div class="card empty-state" style="max-width:420px; margin:40px auto;">
            <div class="icon">⏳</div>
            <h2>AI가 격차 분석을 하고 있습니다</h2>
            <p class="muted">보유 스펙과 백엔드 개발자 요구 기술을 대조하는 중입니다. 화면을 닫지 말고 잠시만 기다려 주세요.</p>
            <ul style="text-align:left; list-style:none; padding:0; font-size:0.88rem; margin-top:14px;">
                <li>✔ 직무 요구 기술 불러오기 · 완료</li>
                <li>● 보유 스펙과 대조하는 중</li>
                <li>○ 로드맵 초안 만들기 · 대기</li>
            </ul>
        </div>
    </c:when>

    <%-- 6-2. AI 응답 실패 (직전 결과 대체) — FR-111/112 --%>
    <c:when test="${state == 'failed'}">
        <h1>격차 분석</h1>
        <p class="muted">목표 직무 백엔드 개발자</p>
        <div class="banner" style="background:var(--danger-bg); border-color:var(--danger); flex-direction:column; align-items:flex-start; gap:10px;">
            <strong style="color:var(--danger);">⚠ AI 응답을 받지 못했습니다</strong>
            <span>응답이 늦어져 새 분석을 끝내지 못했습니다. 대신 2026-09-21에 만든 직전 분석 결과를 보여드립니다.</span>
            <div class="row"><button>다시 시도</button><button class="secondary">직전 결과로 계속 보기</button></div>
        </div>
        <div class="card">
            <div class="spread"><h2>직전 분석 결과</h2><span class="pill">2026-09-21 기준</span></div>
            <p style="margin-top:10px;"><strong style="font-size:1.5rem;">5</strong> / 12개 요구 기술 충족</p>
            <div class="progress-track"><div class="progress-fill teal" style="width:42%;"></div></div>
            <p class="muted">부족한 필수 역량: Kubernetes, JPA, Git · 이후 추가한 스펙은 아직 반영되지 않았습니다.</p>
        </div>
    </c:when>

    <%-- 6-3. 데이터를 불러올 수 없음 --%>
    <c:when test="${state == 'unavailable'}">
        <div class="card empty-state" style="max-width:460px; margin:40px auto;">
            <div class="icon">☁️</div>
            <h2>직무 데이터를 일시적으로 불러올 수 없습니다</h2>
            <p class="muted">직무 요구 기술을 가져오는 곳에 문제가 생겼고, 저장해 둔 데이터도 아직 없습니다. 잠시 후 다시 시도해 주세요.</p>
            <div class="row" style="justify-content:center; margin-top:10px;">
                <button>다시 시도</button>
                <a class="btn secondary" href="${pageContext.request.contextPath}/dashboard">대시보드로 가기</a>
            </div>
        </div>
    </c:when>

    <%-- 희망 직무 미설정 --%>
    <c:when test="${noTargetJob}">
        <div class="card empty-state" style="max-width:460px; margin:40px auto;">
            <div class="icon">🧭</div>
            <h2>희망 직무를 먼저 정해주세요</h2>
            <p class="muted">격차 분석은 목표 직무가 있어야 시작할 수 있습니다. 프로필에서 희망 직무를 선택해주세요.</p>
            <a class="btn" href="${pageContext.request.contextPath}/profile">프로필로 가기</a>
        </div>
    </c:when>

    <%-- 정상 상태(실제 데이터) --%>
    <c:otherwise>
        <h1>격차 분석</h1>
        <p class="muted">목표 직무 <strong>${job.jobName}</strong>의 요구 기술과 지금 가진 스펙을 비교했습니다. 분석일 ${analysis.analyzedAt}</p>

        <div class="two-col" style="margin-top:16px;">
            <div class="primary">
                <div class="card">
                    <h2>한눈에 보기</h2>
                    <p style="margin-top:10px;"><strong style="font-size:1.5rem;">${metCount}</strong> / ${totalCount}개 요구 기술 충족 (일치율 ${analysis.matchRate}%)</p>
                    <div class="progress-track"><div class="progress-fill teal" style="width:${analysis.matchRate}%;"></div></div>
                    <p class="muted" style="margin:0;">지금은 기술명이 정확히 일치하는지로만 판정합니다(대소문자 무시). 의미 기반 매칭은 나중에 고도화 예정입니다.</p>
                </div>

                <div class="card">
                    <h2>충족·부족 목록</h2>
                    <table style="margin-top:10px;">
                        <tr><th>기술</th><th>상태</th></tr>
                        <c:forEach var="item" items="${items}">
                            <tr>
                                <td>${item.skillName}</td>
                                <c:choose>
                                    <c:when test="${item.met}"><td style="color:var(--teal);">✔ 충족</td></c:when>
                                    <c:otherwise><td style="color:var(--danger);">✘ 부족</td></c:otherwise>
                                </c:choose>
                            </tr>
                        </c:forEach>
                    </table>
                </div>

                <div class="row">
                    <form action="${pageContext.request.contextPath}/gap-analysis" method="post">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <button type="submit" class="secondary">다시 분석하기</button>
                    </form>
                    <form action="${pageContext.request.contextPath}/roadmap" method="post">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="generate">
                        <button type="submit">이 결과로 로드맵 만들기</button>
                    </form>
                </div>
            </div>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
