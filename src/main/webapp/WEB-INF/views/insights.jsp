<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="데이터 인사이트 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<%-- FR-45~48 데이터 인사이트. 계산은 InsightService, 여기서는 출력만 한다. --%>
<c:choose>
    <c:when test="${noTargetJob}">
        <div class="card empty-state" style="max-width:460px; margin:40px auto;">
            <div class="icon">📉</div>
            <h2>희망 직무를 먼저 정해주세요</h2>
            <p class="muted">데이터 인사이트는 목표 직무의 채용 데이터를 기준으로 보여줍니다. 아직 정하지 못했다면 직무 찾기에서 추천을 받아보고, 이미 알고 있다면 프로필에서 바로 선택해주세요.</p>
            <div class="row" style="justify-content:center; gap:10px; margin-top:10px;">
                <a class="btn" href="${ctx}/job-discovery">직무 찾기로 가기</a>
                <a class="btn secondary" href="${ctx}/profile">프로필로 가기</a>
            </div>
        </div>
    </c:when>
    <c:otherwise>
<style>
    /* 인사이트 화면 전용 — 데이터가 부족한 카드의 안내 상자 */
    .insight-empty { margin-top:10px; padding:14px 16px; border:1px dashed var(--border); border-radius:8px; text-align:center; }
    .insight-empty .icon { font-size:1.6rem; }
    .insight-empty p { margin:6px 0 10px; }
    .insight-notice { border-left:4px solid var(--primary); }
    .insight-notice ul { margin:8px 0 0; padding-left:18px; }
    .insight-notice li { margin:4px 0; }
    /* 약점 히트맵 — 수준 머리글·칸은 가운데, 분야 열만 왼쪽. 칸마다 세로줄 (2026-10-08) */
    .heatmap-table { margin-top:10px; border:1px solid var(--border); }
    .heatmap-table th, .heatmap-table td { text-align:center; border:1px solid var(--border); }
    .heatmap-table th:first-child, .heatmap-table td:first-child { text-align:left; }
</style>

<h1>데이터 인사이트</h1>
<p class="muted"><strong><c:out value="${insight.jobName}" /></strong> 채용 흐름과 나의 위치를 한 화면에서 봅니다.</p>

<c:if test="${not empty insight.notices}">
    <div class="card insight-notice" style="margin-top:16px;">
        <h2>💡 인사이트를 더 정확하게 보려면</h2>
        <p class="muted" style="margin:4px 0 0;">아직 내 정보가 부족해 일부 카드가 비어 있습니다. 아래를 먼저 해 보세요.</p>
        <ul>
            <c:forEach var="n" items="${insight.notices}">
                <li><c:out value="${n.text}" /> <a href="${ctx}${n.path}"><c:out value="${n.linkLabel}" /> →</a></li>
            </c:forEach>
        </ul>
    </div>
</c:if>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">

        <%-- FR-47 목표 직무 요구 기술의 시간별 변화 --%>
        <div class="card">
            <div class="spread"><h2>요구 기술 변화</h2><span class="pill">채용공고 집계</span></div>
            <c:choose>
                <c:when test="${empty insight.trend.skills}">
                    <div class="insight-empty">
                        <div class="icon">📭</div>
                        <p class="muted">아직 이 직무의 채용공고 집계가 없습니다. 주간 수집이 돌면 자동으로 채워집니다.</p>
                    </div>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="margin-top:4px;">최근 공고에서 기술이 언급된 비율(%) · 최신 달 상위 기술</p>
                    <table style="margin-top:10px; font-size:0.85rem;">
                        <tr>
                            <th style="text-align:left;">기술</th>
                            <c:forEach var="m" items="${insight.trend.months}"><th>${m}</th></c:forEach>
                            <th>전월 대비</th>
                        </tr>
                        <c:forEach var="s" items="${insight.trend.skills}">
                            <tr>
                                <td><c:out value="${s.skillName}" />
                                    <%-- 급상승: 평소(과거 달 EWMA)보다 크게 튄 기술만 (2026-10-08) --%>
                                    <c:if test="${s.rising}"><span class="chip" style="background:var(--danger); color:#fff;">급상승<c:if test="${s.spikeMultiple > 0}"> ${s.spikeMultiple}배</c:if></span></c:if>
                                </td>
                                <c:forEach var="r" items="${s.ratios}">
                                    <td style="min-width:72px;">
                                        <c:choose>
                                            <c:when test="${r == null}"><span class="muted">-</span></c:when>
                                            <c:otherwise>
                                                <div style="display:flex; align-items:center; gap:6px;">
                                                    <div style="flex:1; background:var(--border); border-radius:4px; height:10px;">
                                                        <div style="width:${r}%; height:100%; background:var(--primary); border-radius:4px;"></div>
                                                    </div>
                                                    <span style="width:30px; text-align:right;">${r}</span>
                                                </div>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </c:forEach>
                                <td style="text-align:center;">
                                    <c:choose>
                                        <c:when test="${s.change == null}"><span class="muted">신규</span></c:when>
                                        <c:when test="${s.change > 0}">▲ ${s.change}</c:when>
                                        <c:when test="${s.change < 0}">▼ ${-s.change}</c:when>
                                        <c:otherwise>-</c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                    <p class="muted" style="margin-top:8px; margin-bottom:0; font-size:0.78rem;">공고 수가 적은 달은 비율이 크게 튈 수 있습니다.</p>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- FR-45 또래 비교 --%>
        <div class="card">
            <div class="spread"><h2>또래 비교</h2><span class="pill">스펙 완성도</span></div>
            <c:set var="peer" value="${insight.peer}" />
            <c:if test="${not empty peer.major and not empty peer.grade}">
                <p class="muted" style="margin-top:4px;"><c:out value="${peer.major}" />·<c:out value="${peer.grade}" />학년 평균과 비교<c:if test="${peer.peerCount > 0}"> (비교 대상 ${peer.peerCount}명)</c:if></p>
            </c:if>
            <c:choose>
            <c:when test="${peer.comparable}">
                <div style="margin-top:12px; font-size:0.88rem;">
                    <div class="spread" style="margin-bottom:6px;"><span>나</span><span>${peer.myScore}</span></div>
                    <div class="progress-track" style="margin:0 0 12px;"><div class="progress-fill" style="width:${peer.myScore}%;"></div></div>
                    <div class="spread" style="margin-bottom:6px;"><span>같은 전공·학년 평균</span><span>${peer.peerAverage}</span></div>
                    <div class="progress-track" style="margin:0;"><div class="progress-fill" style="width:${peer.peerAverage}%; background:var(--locked);"></div></div>
                </div>
                <p class="muted" style="margin-top:10px; margin-bottom:0;"><c:out value="${peer.message}" /></p>
            </c:when>
            <c:otherwise>
                <div class="insight-empty">
                    <div class="icon">👥</div>
                    <p class="muted"><c:out value="${peer.message}" /></p>
                    <c:if test="${empty peer.major or empty peer.grade or peer.myScore == null}">
                        <a class="btn secondary" href="${ctx}/profile">프로필 채우기</a>
                    </c:if>
                </div>
            </c:otherwise>
            </c:choose>
        </div>

        <%-- FR-46 합격자 스펙 역산(참고 루트) --%>
        <div class="card">
            <div class="spread"><h2>합격자 참고 루트</h2><span class="pill">예시적 추정</span></div>
            <c:choose>
                <c:when test="${empty insight.benchmark}">
                    <div class="insight-empty">
                        <c:choose>
                            <%-- FR-111 AI 생성 실패 — 잠시 뒤 다시 열면 다시 만든다 (youngjun 2026-10-06) --%>
                            <c:when test="${benchmarkUnavailable}">
                                <div class="icon">⏳</div>
                                <p class="muted">AI 참고 루트를 지금 만들 수 없습니다. 잠시 후 다시 열어 주세요.</p>
                            </c:when>
                            <c:otherwise>
                                <div class="icon">🧭</div>
                                <p class="muted">이 직무의 합격자 참고 루트를 준비 중입니다. 그동안은 로드맵에서 단계별 목표를 확인해 보세요.</p>
                            </c:otherwise>
                        </c:choose>
                        <a class="btn secondary" href="${ctx}/roadmap">로드맵 보기</a>
                    </div>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="margin-top:4px;">실제 합격자 데이터가 아니라, AI가 단계별로 정리한 참고 기준입니다.</p>
                    <table style="margin-top:10px;">
                        <c:forEach var="t" items="${insight.benchmark}">
                            <tr>
                                <td style="width:140px;"><strong><c:out value="${t.label}" /></strong><br><span class="muted" style="font-size:0.78rem;"><c:out value="${t.tier}" /></span></td>
                                <td><c:forEach var="item" items="${t.items}" varStatus="st"><c:out value="${item}" /><c:if test="${!st.last}"> · </c:if></c:forEach></td>
                            </tr>
                        </c:forEach>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>

        <%-- FR-48 부족 역량 약점 히트맵 --%>
        <div class="card">
            <h2>약점 히트맵</h2>
            <c:set var="heat" value="${insight.heatmap}" />
            <c:choose>
                <c:when test="${empty heat.rows}">
                    <div class="insight-empty">
                        <div class="icon">🔍</div>
                        <p class="muted">이 직무로 격차 분석을 한 기록이 없습니다. 분석을 한 번 실행하면 약한 분야가 표시됩니다.</p>
                        <a class="btn secondary" href="${ctx}/gap-analysis">격차 분석 하러 가기</a>
                    </div>
                </c:when>
                <c:when test="${heat.noOwnedSkills}">
                    <div class="insight-empty">
                        <div class="icon">🧩</div>
                        <p class="muted">등록된 보유 기술이 없어 요구 기술 ${heat.missingTotal}개가 모두 부족으로 나옵니다. 프로필에 기술을 입력하고 격차 분석을 다시 실행해 주세요.</p>
                        <div class="row" style="justify-content:center; gap:10px;">
                            <a class="btn" href="${ctx}/profile">기술 입력하기</a>
                            <a class="btn secondary" href="${ctx}/gap-analysis">격차 분석 다시 하기</a>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="margin-top:4px;">분야와 요구 수준별 부족한 기술 수 / 요구 기술 수 · 색이 진할수록 많이 부족합니다</p>
                    <table class="heatmap-table">
                        <tr>
                            <th>분야</th>
                            <c:forEach var="lv" items="${heat.levels}"><th>${lv}</th></c:forEach>
                        </tr>
                        <c:forEach var="row" items="${heat.rows}">
                            <tr>
                                <td><c:out value="${row.category}" /></td>
                                <c:forEach var="cell" items="${row.cells}">
                                    <c:choose>
                                        <c:when test="${cell.total == 0}"><td class="muted">·</td></c:when>
                                        <c:when test="${cell.shade == 3}"><td style="background:#b3702f; color:#fff;">${cell.missing}/${cell.total}</td></c:when>
                                        <c:when test="${cell.shade == 2}"><td style="background:#dba24d;">${cell.missing}/${cell.total}</td></c:when>
                                        <c:when test="${cell.shade == 1}"><td style="background:#f0d9a0;">${cell.missing}/${cell.total}</td></c:when>
                                        <c:otherwise><td>${cell.missing}/${cell.total}</td></c:otherwise>
                                    </c:choose>
                                </c:forEach>
                            </tr>
                        </c:forEach>
                    </table>
                    <p class="muted" style="margin-top:10px; margin-bottom:0;">
                        <c:choose>
                            <c:when test="${heat.missingTotal == 0}">요구 기술을 모두 충족했습니다.</c:when>
                            <c:otherwise><strong>가장 약한 곳</strong> <c:out value="${heat.weakest}" /> · <a href="${ctx}/roadmap">로드맵에서 채우기</a></c:otherwise>
                        </c:choose>
                    </p>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
