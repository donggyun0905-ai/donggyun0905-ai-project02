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

    <%-- 정상 상태 --%>
    <c:otherwise>
        <h1>격차 분석</h1>
        <p class="muted">목표 직무 <strong>백엔드 개발자</strong>의 요구 기술과 지금 가진 스펙을 비교했습니다. 분석일 2026-09-28</p>

        <div class="two-col" style="margin-top:16px;">
            <div class="primary">
                <div class="card">
                    <div class="spread"><h2>한눈에 보기</h2><span class="pill">예시적 추정</span></div>
                    <p style="margin-top:10px;"><strong style="font-size:1.5rem;">6</strong> / 12개 요구 기술 충족</p>
                    <div class="progress-track"><div class="progress-fill teal" style="width:50%;"></div></div>
                    <p class="muted" style="margin:0;">필수 7개 중 5개, 우대 5개 중 1개를 갖췄습니다. 직무 요구 기술은 공식 채용 데이터를 연동하기 전이라 AI가 만든 추정치입니다.</p>
                </div>

                <div class="card">
                    <h2>충족·부족 비교표</h2>
                    <table style="margin-top:10px;">
                        <tr><th>기술</th><th>구분</th><th>상태</th><th>판정 근거</th></tr>
                        <tr><td>Java</td><td>필수</td><td style="color:var(--teal);">✔ 충족</td><td>프로젝트 기술 스택</td></tr>
                        <tr><td>Spring Boot</td><td>필수</td><td style="color:var(--teal);">✔ 충족</td><td>보유 기술 스택</td></tr>
                        <tr><td>MySQL</td><td>필수</td><td style="color:var(--teal);">✔ 충족</td><td>보유 기술 스택</td></tr>
                        <tr><td>Git</td><td>필수</td><td style="color:var(--teal);">✔ 충족</td><td>보유 기술 스택</td></tr>
                        <tr><td>REST API 설계</td><td>필수</td><td style="color:var(--teal);">✔ 충족</td><td>프로젝트 '백엔드 API 서버'와 의미가 가까움</td></tr>
                        <tr><td>JPA</td><td>필수</td><td style="color:var(--danger);">✘ 부족</td><td>관련 스펙 없음</td></tr>
                        <tr><td>Kubernetes</td><td>필수</td><td style="color:var(--danger);">✘ 부족</td><td>관련 스펙 없음</td></tr>
                        <tr><td>Docker</td><td>우대</td><td style="color:var(--teal);">✔ 충족</td><td>보유 기술 스택</td></tr>
                        <tr><td>Redis</td><td>우대</td><td style="color:var(--danger);">✘ 부족</td><td>관련 스펙 없음</td></tr>
                        <tr><td>AWS</td><td>우대</td><td style="color:var(--danger);">✘ 부족</td><td>관련 스펙 없음</td></tr>
                    </table>
                </div>

                <div class="card">
                    <h2>먼저 채울 필수 역량</h2>
                    <div style="margin-top:10px;">
                        <strong>Kubernetes</strong>
                        <p class="muted" style="margin:4px 0;">컨테이너 배포와 확장을 자동화하는 기술입니다. 이미 Docker를 다뤄봤으니 지금 프로젝트를 쿠버네티스에 올려보는 것부터 시작하면 됩니다.</p>
                    </div>
                    <hr style="border:none; border-top:1px solid var(--border);">
                    <div>
                        <strong>JPA</strong>
                        <p class="muted" style="margin:4px 0;">Spring에서 데이터베이스를 객체로 다루는 표준 방식입니다. API 서버 프로젝트의 SQL 코드를 JPA로 바꿔보면 바로 경험이 됩니다.</p>
                    </div>
                </div>

                <div class="row">
                    <form action="${pageContext.request.contextPath}/roadmap" method="post">
                        <input type="hidden" name="action" value="generate">
                        <button type="submit">이 결과로 로드맵 만들기</button>
                    </form>
                    <a class="btn secondary" href="${pageContext.request.contextPath}/job-discovery">다른 직무로 분석하기</a>
                </div>
            </div>
            <div class="side">
                <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
            </div>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
