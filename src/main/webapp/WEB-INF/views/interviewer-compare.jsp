<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 비교 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="spread" style="margin-bottom:16px;">
    <h1 style="margin:0;">지원자 비교</h1>
    <span class="pill">평가 세션 · A사 백엔드 채용 · 2026-10-12 만료</span>
</div>
<p class="muted">받은 공유 링크를 담아 지원자를 나란히 비교합니다. 로그인 없이 이 평가 세션에만 저장되며, 세션이 만료되면 목록도 사라집니다.</p>

<div class="row" style="align-items:stretch; margin-top:16px;">
    <div class="card" style="flex:1;">
        <h2>지원자 담기</h2>
        <div class="row" style="margin-top:10px;">
            <input type="text" placeholder="spec-odyssey.example/share/..." style="flex:1;">
            <button>담기</button>
        </div>
        <p class="muted" style="margin-top:8px;">지금 3명을 담았습니다. 지원자가 공유를 멈추거나 링크가 만료되면 비교표에서 빠집니다.</p>
    </div>
    <div class="card" style="flex:1;">
        <h2>회사 요구 역량</h2>
        <p class="muted" style="margin-top:4px;">가중치가 클수록 적합도 점수에 크게 반영됩니다</p>
        <div style="display:flex; flex-direction:column; gap:8px; margin-top:10px;">
            <div class="row spread"><strong>Java</strong><span class="row"><input type="text" value="가중치 3" style="width:90px;"><button class="link-button">✕</button></span></div>
            <div class="row spread"><strong>Spring Boot</strong><span class="row"><input type="text" value="가중치 3" style="width:90px;"><button class="link-button">✕</button></span></div>
            <div class="row spread"><strong>MySQL</strong><span class="row"><input type="text" value="가중치 2" style="width:90px;"><button class="link-button">✕</button></span></div>
            <div class="row spread"><strong>Kubernetes</strong><span class="row"><input type="text" value="가중치 2" style="width:90px;"><button class="link-button">✕</button></span></div>
            <div class="row spread"><strong>AWS</strong><span class="row"><input type="text" value="가중치 1" style="width:90px;"><button class="link-button">✕</button></span></div>
        </div>
        <div class="row" style="margin-top:12px;"><button class="secondary">역량 추가</button><button>적합도 다시 계산</button></div>
    </div>
</div>

<div class="card">
    <h2>나란히 보기</h2>
    <table style="margin-top:10px;">
        <tr>
            <th>항목</th>
            <th>지원자 1 <a href="${pageContext.request.contextPath}/share/demo" style="margin-left:6px;">이력 보기</a></th>
            <th>지원자 2</th>
            <th>지원자 3</th>
        </tr>
        <tr><td>전공·학년</td><td>컴퓨터공학과 4학년</td><td>소프트웨어학과 4학년</td><td>정보통신공학과 3학년</td></tr>
        <tr><td>자격증</td><td>정보처리산업기사 외 1개</td><td>정보처리기사, SQLD</td><td>없음</td></tr>
        <tr><td>프로젝트</td><td>1개</td><td>3개</td><td>2개</td></tr>
        <tr><td>Java · 가중치 3</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--teal);">갖춤</td></tr>
        <tr><td>Spring Boot · 가중치 3</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--danger);">없음</td></tr>
        <tr><td>MySQL · 가중치 2</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--danger);">없음</td><td style="color:var(--teal);">갖춤</td></tr>
        <tr><td>Kubernetes · 가중치 2</td><td style="color:var(--danger);">없음</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--danger);">없음</td></tr>
        <tr><td>AWS · 가중치 1</td><td style="color:var(--danger);">없음</td><td style="color:var(--teal);">갖춤</td><td style="color:var(--danger);">없음</td></tr>
        <tr style="background:#f1e4c8;"><td><strong>적합도 점수</strong></td><td><strong>73점</strong></td><td><strong>82점</strong></td><td><strong>45점</strong></td></tr>
        <tr><td>성장 잠재력</td><td>빠름 · 4개월간 +18</td><td>보통 · 4개월간 +6</td><td class="muted">지원자가 공개하지 않음</td></tr>
    </table>
    <p class="muted" style="font-size:0.78rem; margin-top:10px; margin-bottom:0;">적합도 = 맞춘 역량의 가중치 합 ÷ 전체 가중치 합(11) × 100. 지원자가 기술 스택을 공개하지 않으면 계산하지 않습니다. 이 비교는 지원자들이 공개한 항목만 사용합니다.</p>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
