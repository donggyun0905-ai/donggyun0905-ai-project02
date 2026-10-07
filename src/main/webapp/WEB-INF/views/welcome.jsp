<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="시작하기 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><c:choose><c:when test="${not empty userName}"><c:out value="${userName}" />님, 환영합니다</c:when><c:otherwise>환영합니다</c:otherwise></c:choose></h1>
<p class="muted" style="max-width:62ch;">스펙 오디세이는 점수만 알려주는 진단이 아니라, 목표 직무까지 가는 <strong>순서 있는 길</strong>을 만들어 매일 한 걸음씩 걷게 하는 서비스입니다. 이렇게 이어집니다.</p>

<%-- FR-115 — 진단 → 길 제시 → 미션. 번호는 실제 순서라서 붙인다 --%>
<div class="row" style="gap:12px; align-items:stretch; margin-top:16px; flex-wrap:wrap;">
    <div class="card" style="flex:1; min-width:240px;">
        <div class="muted" style="font-size:0.8rem;">1단계</div>
        <h2 style="margin:4px 0 6px;">진단</h2>
        <p class="muted" style="margin:0;">프로필에 전공과 할 수 있는 기술을 적고 직무 찾기 설문을 풀면, 목표 직무가 요구하는 기술과 지금 가진 기술을 맞춰 <strong>무엇이 부족한지</strong> 알려 드립니다.</p>
    </div>
    <div class="card" style="flex:1; min-width:240px;">
        <div class="muted" style="font-size:0.8rem;">2단계</div>
        <h2 style="margin:4px 0 6px;">길 제시</h2>
        <p class="muted" style="margin:0;">부족한 기술을 <strong>입문 → 핵심 → 심화 → 전문가</strong> 순서의 길로 바꿉니다. 앞 단계를 끝내야 다음이 열려서, 할 일이 한꺼번에 쏟아지지 않습니다.</p>
    </div>
    <div class="card" style="flex:1; min-width:240px;">
        <div class="muted" style="font-size:0.8rem;">3단계</div>
        <h2 style="margin:4px 0 6px;">미션</h2>
        <p class="muted" style="margin:0;">매일 할 일이 일일 미션으로 나오고, 끝낸 단계는 점수와 연속 기록으로 쌓입니다. 쌓인 이력은 <strong>면접관에게 공유할 링크</strong>로도 쓸 수 있습니다.</p>
    </div>
</div>

<div class="card" style="margin-top:16px;">
    <h2 style="margin:0 0 6px;">먼저 직무 찾기 설문부터</h2>
    <p class="muted" style="margin:0 0 10px;">목표 직무가 있어야 길을 만들 수 있어서 설문이 첫 관문입니다. 5분이면 되고, 나중에 다시 풀어도 됩니다. 이미 목표가 확실하면 프로필에서 바로 고르셔도 됩니다.</p>
    <div class="row" style="gap:10px;">
        <a class="btn" href="${pageContext.request.contextPath}/job-discovery">직무 찾기 설문 시작</a>
        <a class="btn secondary" href="${pageContext.request.contextPath}/profile">프로필에서 직접 고르기</a>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
