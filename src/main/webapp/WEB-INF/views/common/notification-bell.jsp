<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 헤더 알림 버튼 — 등급 배지 왼쪽. 안 읽은 알림이 있으면 빨간 동그라미에 개수를 띄운다.
     데이터(notiUnreadCount·notiRecent)는 NotificationBadgeFilter가 본인 것만 요청에 실어 준다(면접관 계정은 없음).
     누르면 안 읽은 알림 드롭다운. 알림을 누르면 읽음 처리 후 그 화면으로 이동하고, 다음 화면부터 드롭다운에서 빠진다.
     지난(읽은) 알림은 "전체 알림 보기"에서 회색으로 보인다. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:if test="${not empty notiUnreadCount}">
<details class="noti-bell">
    <summary aria-label="알림 ${notiUnreadCount}개">
        <span class="noti-icon" aria-hidden="true">🔔</span>
        <c:if test="${notiUnreadCount > 0}">
            <span class="noti-count">${notiUnreadCount > 99 ? '99+' : notiUnreadCount}</span>
        </c:if>
    </summary>
    <div class="noti-panel">
        <div class="noti-head">
            <strong>알림</strong>
            <c:if test="${notiUnreadCount > 0}">
                <form method="post" action="${ctx}/notifications" class="noti-readall">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="readAll">
                    <button type="submit" class="noti-link-btn">모두 읽음</button>
                </form>
            </c:if>
        </div>
        <c:choose>
            <c:when test="${empty notiRecent}">
                <p class="noti-empty">새 알림이 없습니다.</p>
            </c:when>
            <c:otherwise>
                <ul class="noti-list">
                    <c:forEach var="n" items="${notiRecent}">
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
        <a class="noti-all" href="${ctx}/notifications">전체 알림 보기</a>
    </div>
</details>
<script src="${ctx}/js/notification-bell.js" defer></script>
</c:if>
