<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="알림 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<div class="row" style="justify-content:space-between; align-items:center;">
    <h1>알림</h1>
    <c:if test="${notiUnreadCount > 0}">
        <form method="post" action="${ctx}/notifications">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="readAll">
            <button type="submit" class="secondary">모두 읽음</button>
        </form>
    </c:if>
</div>
<p class="muted">내 글의 댓글·답글, 면접관의 공유 링크 열람, D-day 하루 전·당일, 오늘의 미션 마감 1시간 전에 알려드립니다. 확인한 알림은 회색으로 남고, 최근 50개까지 보여줍니다.</p>

<div class="noti-page-list" style="margin-top:16px;">
    <c:choose>
        <c:when test="${empty notifications}">
            <p class="noti-empty">아직 받은 알림이 없습니다.</p>
        </c:when>
        <c:otherwise>
            <ul class="noti-list">
                <c:forEach var="n" items="${notifications}">
                    <li>
                        <form method="post" action="${ctx}/notifications">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="action" value="open">
                            <input type="hidden" name="id" value="${n.id}">
                            <button type="submit" class="noti-item ${n.read ? 'read' : 'unread'}">
                                <span class="noti-msg"><c:out value="${n.message}" /></span>
                                <span class="noti-time"><c:out value="${n.createdAtText}" /></span>
                            </button>
                        </form>
                    </li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
