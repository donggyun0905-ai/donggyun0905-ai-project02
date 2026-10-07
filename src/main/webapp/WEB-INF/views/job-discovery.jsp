<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="직무 찾기 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="hasResults" value="${not empty recommendations}" />
<c:set var="activePill" value="background:var(--primary); color:#fff; border-color:var(--primary);" />

<h1>직무 찾기</h1>
<c:if test="${not empty onboardingNotice}">
    <div class="banner"><span><c:out value="${onboardingNotice}" /></span></div>
</c:if>
<%-- 설문 문항이 바뀌어 아직 답하지 않은 문항이 있을 때 — 새 문항에는 "새 문항" 표시가 붙는다 --%>
<c:if test="${newQuestionCount > 0}">
    <div class="banner"><span><span class="ic ic-bell" aria-hidden="true"></span> 설문 문항이 <strong><c:out value="${newQuestionCount}" />개</strong> 새로 생겼어요. 새 문항에 답하고 다시 제출하면 추천 직무가 더 정확해집니다.</span></div>
</c:if>
<%-- FR-37 추천을 받은 뒤 프로필(전공·기술 등)이 바뀌었을 때 — 다시 제출하면 바뀐 프로필로 추천한다 --%>
<c:if test="${hasResults && profileChanged}">
    <div class="banner"><span><span class="ic ic-bell" aria-hidden="true"></span> 추천을 받은 뒤 프로필(전공·기술 등)이 바뀌었어요. 설문을 다시 제출하면 바뀐 프로필로 추천 직무를 새로 받을 수 있습니다.</span></div>
</c:if>
<p class="muted">희망 직무가 아직 정해지지 않았다면, 간단한 설문과 지금까지 쌓은 전공·스펙을 함께 보고 어울리는 직무를 찾아드립니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="margin-bottom:14px;">
            <span class="pill" style="${hasResults ? '' : activePill}">설문</span>
            <span class="pill" style="${hasResults ? activePill : ''}">후보 직무 고르기</span>
            <span class="pill">격차 분석</span>
        </div>

        <div class="card">
            <h2>흥미·성향 설문</h2>
            <p class="muted">가장 가까운 답을 하나씩 골라주세요. 정답은 없습니다.</p>

            <c:choose>
                <c:when test="${empty questions}">
                    <div class="empty-state">
                        <p>설문 문항이 아직 준비되지 않았습니다.</p>
                        <p class="muted">관리자: <code>sql/05_seed_survey.sql</code>을 실행해 주세요.</p>
                    </div>
                </c:when>
                <c:otherwise>
                    <form action="${ctx}/job-discovery" method="post">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="survey">
                        <c:forEach var="q" items="${questions}" varStatus="qs">
                            <div style="margin-top:14px;">
                                <p><strong>${qs.count}. <c:out value="${q.content}" /></strong>
                                    <c:if test="${not empty myAnswers && empty myAnswers[q.id]}"> <span class="chip chip-gold" style="margin-left:6px;">새 문항</span></c:if></p>
                                <div class="row">
                                    <c:forTokens var="label" items="전혀 아니다,아니다,보통,그렇다,매우 그렇다" delims="," varStatus="os">
                                        <label style="display:inline; width:auto;">
                                            <input type="radio" name="q${q.id}" value="${os.count}" style="width:auto;"
                                                   ${myAnswers[q.id] == os.count ? 'checked' : ''} ${os.first ? 'required' : ''}>
                                            <c:out value='${label}' />
                                        </label>
                                    </c:forTokens>
                                </div>
                            </div>
                        </c:forEach>
                        <button type="submit" style="margin-top:16px;">
                            ${hasResults ? '다시 제출하고 후보 새로 받기' : '설문 제출하고 후보 보기'}
                        </button>
                    </form>
                </c:otherwise>
            </c:choose>
        </div>

        <c:if test="${hasResults}">
            <div class="card" id="results">
                <div class="spread"><h2>어울리는 직무 후보</h2><span class="pill">예시적 추정</span></div>
                <p class="muted" style="margin-top:6px;">설문 응답과 보유 스펙을 함께 보고 고른 후보입니다. 하나를 고르면 바로 격차 분석으로 이어집니다.</p>

                <c:forEach var="r" items="${recommendations}">
                    <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:14px;">
                        <span class="chip ${r.rankOrder == 1 ? 'chip-gold' : 'chip-locked'}">추천 ${r.rankOrder}순위</span>
                        <strong><c:out value="${r.jobName}" /></strong>
                        <%-- FR-38 ② 전공이 이 계열과 뚜렷이 가까워 점수에 반영된 후보 --%>
                        <c:if test="${not empty r.summary.major}">
                            <span class="chip chip-gold" title="전공(${fn:escapeXml(r.summary.major)})이 이 분야와 가까워 추천 점수에 반영했어요">전공 반영</span>
                        </c:if>
                        <c:if test="${r.selected}"><span class="pill">선택함</span></c:if>
                        <p style="margin:8px 0;"><strong>추천 이유</strong> <c:out value="${r.matchReason}" /></p>
                        <%-- FR-35 직무 요약 — 하는 일·전망은 AI(실패 시 기본 문구), 필요 역량은 요구 기술 데이터 --%>
                        <c:choose>
                            <c:when test="${not empty r.summary}">
                                <details style="margin:0 0 10px;">
                                    <summary style="cursor:pointer; color:var(--primary);">이 직무 알아보기</summary>
                                    <div style="margin-top:8px; padding:10px 12px; border:1px solid var(--border); border-radius:8px;">
                                        <p style="margin:0 0 8px;"><strong>하는 일</strong> <c:out value="${r.summary.duties}" /></p>
                                        <c:if test="${not empty r.summary.skills}">
                                            <p style="margin:0 0 8px;"><strong>필요 역량</strong>
                                                <c:forEach var="skill" items="${r.summary.skills}">
                                                    <span class="chip chip-locked" style="margin:2px 2px 0 0;"><c:out value="${skill}" /></span>
                                                </c:forEach>
                                            </p>
                                        </c:if>
                                        <p style="margin:0;"><strong>전망</strong> <c:out value="${r.summary.outlook}" /></p>
                                        <%-- 전망 근거 — 수집된 공고 데이터(JOB_SKILL_TREND) 숫자 그대로. 데이터가 부족한 직무는 안 보인다 --%>
                                        <c:if test="${not empty r.summary.trend}">
                                            <p style="margin:8px 0 0;"><strong>최근 공고 동향</strong>
                                                <span class="muted" style="font-size:0.8rem;">(<c:out value="${r.summary.trend.month}" /> 기준)</span></p>
                                            <p style="margin:4px 0 0;">많이 찾는 기술
                                                <c:forEach var="t" items="${r.summary.trend.hot}">
                                                    <span class="chip" style="margin:2px 2px 0 0;"><c:out value="${t}" /></span>
                                                </c:forEach>
                                            </p>
                                            <c:if test="${not empty r.summary.trend.rising}">
                                                <p style="margin:4px 0 0;">지난달보다 늘어난 기술
                                                    <c:forEach var="t" items="${r.summary.trend.rising}">
                                                        <span class="chip chip-gold" style="margin:2px 2px 0 0;"><c:out value="${t.name}" /> +${t.change}%p</span>
                                                    </c:forEach>
                                                </p>
                                            </c:if>
                                        </c:if>
                                        <p class="muted" style="margin:8px 0 0; font-size:0.78rem;">
                                            ${r.summary.ai ? 'AI가 정리한 일반적인 설명입니다. 필요 역량은 수집된 요구 기술 기준입니다.'
                                                           : '기본 설명입니다. 필요 역량은 수집된 요구 기술 기준입니다.'}
                                        </p>
                                    </div>
                                </details>
                            </c:when>
                            <c:otherwise>
                                <p class="muted" style="margin:0 0 10px; font-size:0.85rem;">다시 제출하면 이 직무가 하는 일·필요 역량·전망 요약을 볼 수 있어요.</p>
                            </c:otherwise>
                        </c:choose>
                        <form action="${ctx}/job-discovery" method="post">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="action" value="select">
                            <input type="hidden" name="recommendationId" value="${r.id}">
                            <button type="submit" class="${r.rankOrder == 1 ? '' : 'secondary'}">이 직무로 격차 분석하기</button>
                        </form>
                    </div>
                </c:forEach>
            </div>
        </c:if>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
