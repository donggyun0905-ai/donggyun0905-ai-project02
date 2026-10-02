<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="복구 코드 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>
        <c:choose>
            <c:when test="${recoveryContext == 'RESET'}">비밀번호를 바꿨습니다</c:when>
            <c:otherwise>가입이 완료됐습니다</c:otherwise>
        </c:choose>
    </h1>
    <div class="card">
        <h2 style="margin-bottom:6px;">복구 코드를 꼭 보관해 주세요</h2>
        <p class="muted">비밀번호를 잊었을 때 이 코드로 새 비밀번호를 정할 수 있습니다.
            <c:if test="${recoveryContext == 'RESET'}">방금 쓴 코드는 사용이 끝났고, 아래가 <strong>새 코드</strong>입니다.</c:if></p>
        <p style="font-family:'JetBrains Mono', Consolas, monospace; font-size:1.5rem; letter-spacing:0.08em; text-align:center; background:#fff; border:2px dashed var(--gold); border-radius:8px; padding:16px 8px; margin:14px 0; user-select:all;"><c:out value="${recoveryCode}" /></p>
        <ul class="muted" style="font-size:0.88rem; padding-left:18px;">
            <li>이 화면을 닫으면 <strong>다시 볼 수 없습니다.</strong> 비밀번호 관리자나 메모장 등 안전한 곳에 저장하세요.</li>
            <li>코드를 아는 사람은 누구나 비밀번호를 바꿀 수 있으니 다른 사람에게 보여 주지 마세요.</li>
            <li>잃어버려도 로그인한 뒤 내 프로필에서 새로 발급받을 수 있습니다(이전 코드는 쓸 수 없게 됩니다).</li>
        </ul>
        <a class="btn" href="${pageContext.request.contextPath}/login" style="display:block; text-align:center; margin-top:10px;">로그인하러 가기</a>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
