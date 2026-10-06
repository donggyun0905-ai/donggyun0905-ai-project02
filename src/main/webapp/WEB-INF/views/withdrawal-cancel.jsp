<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="탈퇴 취소 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="center-box">
    <h1>탈퇴 신청한 계정입니다</h1>

    <div class="card">
        <p>이 계정은 탈퇴 신청 상태입니다. <strong><c:out value="${purgeDate}" /></strong>까지는 탈퇴를 취소하고
            예전 기록(로드맵·스펙·서류)을 그대로 이어서 쓸 수 있습니다.</p>
        <p class="muted" style="font-size:0.85rem;">이 날짜가 지나면 탈퇴가 확정되어 개인정보가 지워지고, 같은 아이디로 새로 가입할 수 있게 됩니다.</p>
        <c:if test="${not empty errorMessage}">
            <p style="color:var(--danger); font-size:0.85rem;"><c:out value="${errorMessage}" /></p>
        </c:if>
        <form action="${pageContext.request.contextPath}/login" method="post" style="margin-top:12px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="cancelWithdrawal">
            <c:choose>
                <c:when test="${recoveryCodeRequired}">
                    <%-- 비밀번호만으로 되살리면 비밀번호가 새어 나간 계정을 남이 되살릴 수 있어서, 가입 때 받은 복구 코드까지 받는다 --%>
                    <p><label>복구 코드</label>
                        <input type="text" name="recoveryCode" required autocomplete="off" autofocus
                               placeholder="가입할 때 받은 복구 코드" style="width:100%;"></p>
                    <p class="muted" style="font-size:0.82rem; margin-top:-4px;">가입할 때(또는 프로필에서 재발급할 때) 한 번 보여 준 코드입니다.
                        코드를 잃어버렸다면 <a href="${pageContext.request.contextPath}/password-reset">비밀번호 찾기</a>에서 새 코드를 받을 수 있습니다.</p>
                </c:when>
                <c:otherwise>
                    <p class="muted" style="font-size:0.82rem;">이 계정은 복구 코드를 받아 두기 전에 만들어져서, 비밀번호 확인만으로 되살립니다.</p>
                </c:otherwise>
            </c:choose>
            <button type="submit" style="width:100%;">탈퇴 취소하고 계속 쓰기</button>
        </form>
        <p style="text-align:center; margin-top:10px;">
            <a href="${pageContext.request.contextPath}/login">그대로 탈퇴 상태로 두기</a>
        </p>
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
