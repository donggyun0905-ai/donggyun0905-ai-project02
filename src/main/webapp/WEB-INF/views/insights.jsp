<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="데이터 인사이트 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>데이터 인사이트</h1>
<p class="muted">백엔드 개발자 채용 흐름과 나의 위치를 한 화면에서 봅니다. 지금은 샘플 데이터와 AI 추정치를 기준으로 하며, 공식 채용 데이터가 연동되면 바뀝니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="card">
            <div class="spread"><h2>취업시장 트렌드</h2><span class="pill">예시적 추정</span></div>
            <p class="muted" style="margin-top:4px;">최근 공고에서 자주 언급된 기술 순위</p>
            <div style="display:flex; flex-direction:column; gap:8px; margin-top:12px; font-size:0.85rem;">
                <div class="spread"><span style="width:110px;">Spring Boot</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:95%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">Java</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:85%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">MySQL</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:70%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">Docker</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:64%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">Kubernetes</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:52%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">AWS</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:44%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">Redis</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:34%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
                <div class="spread"><span style="width:110px;">JPA</span><div style="flex:1; background:var(--border); border-radius:4px; height:14px; margin:0 8px;"><div style="width:28%; height:100%; background:var(--primary); border-radius:4px;"></div></div></div>
            </div>
        </div>

        <div class="card">
            <div class="spread"><h2>또래 비교</h2><span class="pill">실제 데이터</span></div>
            <c:choose>
                <c:when test="${not empty peerComparison and peerComparison.peerAverage != null}">
                    <p class="muted" style="margin-top:4px;">같은 전공·학년 <c:out value="${peerComparison.peerCount}" />명 평균과 스펙 완성도 비교</p>
                    <div style="margin-top:12px; font-size:0.88rem;">
                        <div class="spread" style="margin-bottom:6px;"><span>나</span><span><c:out value="${peerComparison.myScore}" /></span></div>
                        <div class="progress-track" style="margin:0 0 12px;"><div class="progress-fill" style="width:${peerComparison.myScore}%;"></div></div>
                        <div class="spread" style="margin-bottom:6px;"><span>같은 전공·학년 평균</span><span><c:out value="${peerComparison.peerAverage}" /></span></div>
                        <div class="progress-track" style="margin:0;"><div class="progress-fill" style="width:${peerComparison.peerAverage}%; background:var(--locked);"></div></div>
                    </div>
                    <p class="muted" style="margin-top:10px; margin-bottom:0;"><c:out value="${peerDiffMessage}" /></p>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="margin-top:4px;">아직 같은 전공·학년의 비교 데이터가 없습니다. 사용자가 더 모이면 표시됩니다.</p>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="card">
            <div class="spread"><h2>합격자 참고 루트</h2><span class="pill">예시적 추정</span></div>
            <p class="muted" style="margin-top:4px;">실제 합격자 데이터가 아니라, AI가 단계별로 정리한 참고 기준입니다.</p>
            <c:choose>
                <c:when test="${empty benchmark}">
                    <p class="muted" style="margin-top:10px;">
                        <c:choose>
                            <c:when test="${empty sessionScope.loginUser}">희망 직무를 설정하면 참고 루트를 보여드립니다.</c:when>
                            <c:otherwise>아직 생성된 참고 루트가 없습니다. 희망 직무를 프로필에서 설정했는지 확인해주세요.</c:otherwise>
                        </c:choose>
                    </p>
                </c:when>
                <c:otherwise>
                    <table style="margin-top:10px;">
                        <c:forEach var="tierGroup" items="${benchmark}">
                            <c:set var="tierLabel" value="${tierGroup.tier == 'ENTRY' ? '입문' : tierGroup.tier == 'CORE' ? '핵심' : tierGroup.tier == 'ADVANCED' ? '심화' : '전문가'}" />
                            <tr>
                                <td style="width:140px;"><strong>${tierLabel}</strong><br><span class="muted" style="font-size:0.78rem;">${tierGroup.tier}</span></td>
                                <td>
                                    <c:forEach var="item" items="${tierGroup.items}" varStatus="st">${item.content}${st.last ? '' : ' · '}</c:forEach>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>

        <div class="card">
            <h2>약점 히트맵</h2>
            <p class="muted" style="margin-top:4px;">분야와 단계별로 부족한 기술 수 · 색이 진할수록, 숫자가 클수록 많이 부족합니다</p>
            <table style="margin-top:10px; text-align:center;">
                <tr><th style="text-align:left;">분야</th><th>입문</th><th>핵심</th><th>심화</th><th>전문가</th></tr>
                <tr><td style="text-align:left;">언어</td><td>0</td><td>0</td><td style="background:#f0d9a0;">1</td><td style="background:#f0d9a0;">1</td></tr>
                <tr><td style="text-align:left;">프레임워크</td><td>0</td><td style="background:#f0d9a0;">1</td><td style="background:#f0d9a0;">1</td><td style="background:#dba24d;">2</td></tr>
                <tr><td style="text-align:left;">데이터베이스</td><td>0</td><td style="background:#f0d9a0;">1</td><td style="background:#dba24d;">2</td><td style="background:#f0d9a0;">1</td></tr>
                <tr><td style="text-align:left;">인프라·배포</td><td style="background:#f0d9a0;">1</td><td style="background:#dba24d;">2</td><td style="background:#b3702f; color:#fff;">3</td><td style="background:#b3702f; color:#fff;">3</td></tr>
                <tr><td style="text-align:left;">협업·도구</td><td>0</td><td style="background:#f0d9a0;">1</td><td style="background:#f0d9a0;">1</td><td style="background:#dba24d;">2</td></tr>
            </table>
            <p class="muted" style="margin-top:10px; margin-bottom:0;"><strong>가장 약한 곳</strong> 인프라·배포 분야의 심화·전문가 단계 · <a href="${pageContext.request.contextPath}/roadmap">로드맵에서 채우기</a></p>
        </div>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
