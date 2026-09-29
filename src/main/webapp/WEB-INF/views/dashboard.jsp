<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="대시보드 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="banner">
    <span>⏱️ 마감 임박 <strong>D-3</strong> 정보처리기사 필기 원서 접수가 10월 1일에 마감됩니다</span>
    <a href="${pageContext.request.contextPath}/dday">일정 보기</a>
</div>

<h1>대시보드</h1>
<p class="muted">목표 직무 <strong>백엔드 개발자</strong> · 지금은 핵심(CORE) 단계를 걷고 있습니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="align-items:stretch;">
            <div class="card" style="flex:1;">
                <h2>스펙 완성도</h2>
                <div class="row" style="margin-top:12px; align-items:center; gap:18px;">
                    <div style="font-size:2.2rem; font-weight:700; color:var(--primary);">62<span style="font-size:1rem; color:var(--ink-soft); font-weight:400;"> / 100</span></div>
                    <div style="font-size:0.85rem; line-height:1.9;">
                        자격증 <strong>2개</strong> · 프로젝트 <strong>1개</strong><br>
                        보유 기술 <strong>4개</strong> · 기준 <strong>백엔드 개발자</strong>
                    </div>
                </div>
            </div>
            <div class="card" style="flex:1;">
                <h2>나의 등급</h2>
                <div class="row" style="margin-top:10px; gap:14px;">
                    <img src="${pageContext.request.contextPath}${empty tierLogoPath ? '/image/tier-3-practitioner.png' : tierLogoPath}" height="52">
                    <div>
                        <strong style="font-size:1.1rem;">${empty currentTier.tierName ? '실전러' : currentTier.tierName}</strong>
                        <div class="muted" style="font-size:0.8rem;">정식 항해사 · 누적 ${empty totalScore ? '1700' : totalScore}점</div>
                    </div>
                </div>
                <div class="progress-track" style="margin-top:10px;"><div class="progress-fill" style="width:57%;"></div></div>
                <p class="muted" style="font-size:0.8rem; margin:0;">다음 등급 '취뽀 임박(선장)'까지 1300점 · 미션 연속 4일째</p>
            </div>
        </div>

        <div class="card">
            <h2>여정 지도</h2>
            <div class="row" style="margin-top:14px; justify-content:space-between;">
                <div style="text-align:center;"><div class="chip chip-teal">✔</div><div style="margin-top:6px; font-weight:600;">입문 ENTRY</div><div class="muted" style="font-size:0.78rem;">완료</div></div>
                <div style="flex:1; height:2px; background:var(--teal); margin-top:14px;"></div>
                <div style="text-align:center;"><div class="chip chip-gold">⛵</div><div style="margin-top:6px; font-weight:600;">핵심 CORE</div><div class="muted" style="font-size:0.78rem;">지금 여기</div></div>
                <div style="flex:1; height:2px; background:var(--border); margin-top:14px;"></div>
                <div style="text-align:center;"><div class="chip chip-locked">○</div><div style="margin-top:6px; font-weight:600;">심화 ADVANCED</div><div class="muted" style="font-size:0.78rem;">다음 목적지</div></div>
                <div style="flex:1; height:2px; background:var(--border); margin-top:14px;"></div>
                <div style="text-align:center;"><div class="chip chip-locked">🏁</div><div style="margin-top:6px; font-weight:600;">전문가 EXPERT</div><div class="muted" style="font-size:0.78rem;">최종 목적지</div></div>
            </div>
            <p style="margin-top:16px; margin-bottom:0;"><strong>지금 할 일</strong> 쿠버네티스에 API 서버 올려보기 · <a href="${pageContext.request.contextPath}/roadmap">로드맵에서 보기</a></p>
        </div>

        <div class="row" style="align-items:stretch;">
            <div class="card" style="flex:1;">
                <h2>오늘의 미션</h2>
                <p style="margin:10px 0 6px;"><strong>2 / 3</strong> 완료</p>
                <div class="progress-track"><div class="progress-fill teal" style="width:66%;"></div></div>
                <ul style="margin:10px 0; padding-left:18px; font-size:0.88rem;">
                    <li>DFS와 BFS · 완료</li>
                    <li>구간 합 구하기 변형 · 완료</li>
                    <li>최단 경로 · 남음</li>
                </ul>
                <a href="${pageContext.request.contextPath}/mission">미션 하러 가기</a>
            </div>
            <div class="card" style="flex:1;">
                <h2>다가오는 일정</h2>
                <ul style="list-style:none; margin:10px 0; padding:0; font-size:0.88rem; display:flex; flex-direction:column; gap:6px;">
                    <li><span class="chip chip-danger">D-3</span> 정보처리기사 필기 접수 마감</li>
                    <li><span class="chip chip-gold">D-12</span> SQLD 시험</li>
                    <li><span class="chip chip-gold">D-18</span> A사 신입 공채 서류 마감</li>
                </ul>
                <a href="${pageContext.request.contextPath}/dday">전체 일정 보기</a>
            </div>
        </div>

        <div class="card spread">
            <div>
                <h2>부족한 필수 역량 2개</h2>
                <p class="muted" style="margin-top:6px;">Kubernetes, JPA · 요구 기술 12개 중 6개 충족</p>
            </div>
            <a class="btn" href="${pageContext.request.contextPath}/gap-analysis">격차 분석 보기</a>
        </div>
    </div>

    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
