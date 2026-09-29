<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="D-day 알림 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>D-day 알림</h1>
<p class="muted">자격증 접수일과 공채 마감일을 놓치지 않도록 모아둡니다. 로드맵에 있는 자격증은 시험 일정이 자동으로 등록됩니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="banner">
            <span><strong style="font-size:1.3rem; color:var(--danger);">D-3</strong> &nbsp;정보처리기사 필기 원서 접수 마감</span>
            <span class="muted">2026-10-01 · 로드맵의 자격증 단계와 연결된 일정입니다</span>
        </div>

        <div class="card">
            <h2>일정 직접 추가</h2>
            <form class="row" style="margin-top:10px; align-items:flex-end;">
                <span style="flex:2;"><label>제목</label><input type="text" placeholder="예) B사 인턴 서류 마감"></span>
                <span style="flex:1;"><label>날짜</label><input type="date"></span>
                <span style="flex:1;"><label>종류</label><input type="text" placeholder="공채"></span>
                <button type="submit">추가하기</button>
            </form>
        </div>

        <div class="card">
            <h2>다가오는 일정</h2>
            <ul class="item-list" style="margin-top:10px;">
                <li class="spread"><span><strong>정보처리기사 필기 원서 접수 마감</strong><br><span class="muted" style="font-size:0.82rem;">2026-10-01 · 자격증 · 로드맵에서 자동 등록</span></span><span class="chip chip-danger">D-3</span></li>
                <li class="spread"><span><strong>SQLD 시험</strong><br><span class="muted" style="font-size:0.82rem;">2026-10-10 · 자격증 · 로드맵에서 자동 등록</span></span><span class="chip chip-gold">D-12</span></li>
                <li class="spread"><span><strong>A사 신입 공채 서류 마감</strong><br><span class="muted" style="font-size:0.82rem;">2026-10-16 · 공채 · 직접 추가</span></span><span class="chip chip-gold">D-18</span></li>
                <li class="spread"><span><strong>정보처리기사 필기 시험</strong><br><span class="muted" style="font-size:0.82rem;">2026-10-25 · 자격증 · 로드맵에서 자동 등록</span></span><span class="chip chip-gold">D-27</span></li>
                <li class="spread"><span><strong>정보처리산업기사 합격 발표</strong><br><span class="muted" style="font-size:0.82rem;">2026-09-23 · 자격증</span></span><span class="chip chip-locked">지남</span></li>
            </ul>
            <p style="margin-top:10px; margin-bottom:0; font-size:0.88rem;">
                <label style="display:inline; width:auto;"><input type="checkbox" style="width:auto;"> 마감 3일 전에 이메일로 알림 받기</label>
            </p>
            <p class="muted" style="font-size:0.8rem;">알림 받을 주소: demo@spec-odyssey.com · <a href="${pageContext.request.contextPath}/profile">내 프로필에서 바꾸기</a></p>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
