<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="오늘의 미션 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>오늘의 미션</h1>
<p class="muted">2026-09-28 월요일 · 매일 코딩테스트 문제 3개를 풀며 여정을 이어갑니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="row" style="align-items:stretch;">
            <div class="card" style="flex:1;">
                <h2>연속 4일째</h2>
                <p class="muted" style="margin-top:6px;">오늘 미션을 끝내면 5일이 됩니다</p>
                <div class="row" style="margin-top:10px; gap:6px;">
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">화 22<br>쉼</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">수 23<br>쉼</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">목 24<br>완료</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">금 25<br>완료</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">토 26<br>완료</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px;">일 27<br>완료</div>
                    <div class="pill" style="text-align:center; font-size:0.72rem; padding:6px 8px; background:var(--gold); color:#fff; border-color:var(--gold);">월 28<br>오늘</div>
                </div>
            </div>
            <div class="card" style="flex:1;">
                <h2>문제 난이도</h2>
                <p style="margin-top:8px;">현재 등급 <strong>실전러</strong>에 맞춰 <strong>Lv2~3</strong> 문제가 나옵니다.</p>
                <p class="muted" style="font-size:0.82rem;">등급이 오르면 더 높은 난이도가 섞여 나옵니다. 점수: 정답 +10~30(난이도별), 풀이 +5</p>
            </div>
        </div>

        <div class="card">
            <div class="spread"><h2>오늘의 문제 3개</h2><span class="muted">2 / 3 완료</span></div>
            <ul class="item-list" style="margin-top:12px;">
                <li class="completed">
                    <div class="spread">
                        <strong>DFS와 BFS</strong>
                        <span class="chip chip-teal">✔ 완료 · 정답</span>
                    </div>
                    <p class="muted" style="margin:6px 0;">링크 추천 · 백준 1260 · Lv2 · 정답 +20, 풀이 +5 적립</p>
                    <button class="secondary">문제 다시 보기</button>
                </li>
                <li>
                    <div class="spread">
                        <strong>구간 합 구하기 변형</strong>
                        <span class="chip chip-danger">✘ 완료 · 오답</span>
                    </div>
                    <p class="muted" style="margin:6px 0;">AI 생성 문제 · Lv2 · 풀이 +5 적립</p>
                    <div class="row"><button class="secondary">해설 보기</button><button class="secondary">다시 풀기</button></div>
                </li>
                <li>
                    <div class="spread">
                        <strong>최단 경로</strong>
                        <span class="chip chip-gold">남음</span>
                    </div>
                    <p class="muted" style="margin:6px 0;">오픈 라이선스 문제셋 · Lv3 · 정답 시 +25, 풀이 +5</p>
                    <div class="row"><button>풀러 가기</button><button class="secondary">완료 체크</button></div>
                </li>
            </ul>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
