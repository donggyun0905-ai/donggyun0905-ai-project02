<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="직무 찾기 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>직무 찾기</h1>
<p class="muted">희망 직무가 아직 정해지지 않았다면, 간단한 설문과 지금까지 쌓은 전공·스펙을 함께 보고 어울리는 직무를 찾아드립니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="margin-bottom:14px;">
            <span class="pill" style="background:var(--primary); color:#fff; border-color:var(--primary);">설문</span>
            <span class="pill">후보 직무 고르기</span>
            <span class="pill">격차 분석</span>
        </div>

        <div class="card">
            <h2>흥미·성향 설문</h2>
            <p class="muted">가장 가까운 답을 하나씩 골라주세요. 정답은 없습니다.</p>
            <div style="margin-top:14px;">
                <p><strong>1. 화면을 직접 만들고 바로 결과를 보는 일이 즐겁다</strong></p>
                <div class="row">
                    <label style="display:inline; width:auto;"><input type="radio" name="q1" style="width:auto;"> 전혀 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q1" style="width:auto;"> 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q1" style="width:auto;" checked> 보통</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q1" style="width:auto;"> 그렇다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q1" style="width:auto;"> 매우 그렇다</label>
                </div>
            </div>
            <div style="margin-top:14px;">
                <p><strong>2. 데이터를 모으고 정리해서 의미를 찾는 일이 즐겁다</strong></p>
                <div class="row">
                    <label style="display:inline; width:auto;"><input type="radio" name="q2" style="width:auto;"> 전혀 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q2" style="width:auto;"> 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q2" style="width:auto;"> 보통</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q2" style="width:auto;" checked> 그렇다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q2" style="width:auto;"> 매우 그렇다</label>
                </div>
            </div>
            <div style="margin-top:14px;">
                <p><strong>3. 서버나 시스템이 안정적으로 돌아가게 만드는 일에 관심이 있다</strong></p>
                <div class="row">
                    <label style="display:inline; width:auto;"><input type="radio" name="q3" style="width:auto;"> 전혀 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q3" style="width:auto;"> 아니다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q3" style="width:auto;"> 보통</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q3" style="width:auto;"> 그렇다</label>
                    <label style="display:inline; width:auto;"><input type="radio" name="q3" style="width:auto;" checked> 매우 그렇다</label>
                </div>
            </div>
            <button style="margin-top:16px;">설문 제출하고 후보 보기</button>
        </div>

        <div class="card">
            <div class="spread"><h2>어울리는 직무 후보</h2><span class="pill">예시적 추정</span></div>
            <p class="muted" style="margin-top:6px;">설문 응답과 보유 스펙·전공을 함께 보고 고른 후보입니다. 하나를 고르면 바로 격차 분석으로 이어집니다.</p>

            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:14px;">
                <span class="chip chip-gold">추천 1순위</span> <strong>백엔드 개발자</strong>
                <p style="margin:8px 0;"><strong>추천 이유</strong> 서버·시스템 안정성 문항에 가장 높게 답했고, 보유 기술(Spring Boot, MySQL)과 API 서버 프로젝트가 이 직무 요구 기술과 가장 많이 겹칩니다.</p>
                <p class="muted" style="margin:4px 0; font-size:0.84rem;"><strong>하는 일</strong> 서비스의 서버와 API, 데이터 처리 로직을 만들고 운영합니다.</p>
                <form action="${pageContext.request.contextPath}/gap-analysis" method="get"><button type="submit">이 직무로 격차 분석하기</button></form>
            </div>
            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:14px;">
                <span class="chip chip-locked">추천 2순위</span> <strong>데이터 엔지니어</strong>
                <p style="margin:8px 0;"><strong>추천 이유</strong> 데이터 정리 문항 응답이 높고, MySQL과 Docker 경험이 데이터 파이프라인 작업의 기초와 이어집니다.</p>
                <form action="${pageContext.request.contextPath}/gap-analysis" method="get"><button type="submit" class="secondary">이 직무로 격차 분석하기</button></form>
            </div>
            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:14px;">
                <span class="chip chip-locked">추천 3순위</span> <strong>DevOps 엔지니어</strong>
                <p style="margin:8px 0;"><strong>추천 이유</strong> 시스템 안정성에 관심이 높고, Docker로 배포까지 해본 경험이 있습니다.</p>
                <form action="${pageContext.request.contextPath}/gap-analysis" method="get"><button type="submit" class="secondary">이 직무로 격차 분석하기</button></form>
            </div>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
