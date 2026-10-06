<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
                                <p><strong>${qs.count}. <c:out value="${q.content}" /></strong></p>
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
                        <c:if test="${r.selected}"><span class="pill">선택함</span></c:if>
                        <p style="margin:8px 0;"><strong>추천 이유</strong> <c:out value="${r.matchReason}" /></p>
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
