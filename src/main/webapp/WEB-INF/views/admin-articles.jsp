<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 게시판 관리 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="articles" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<p class="muted">스펙 아카이브는 자동 공개 + 사후 관리 방식입니다. 대기 중인 신고를 먼저 처리하고,
    아래 글 목록에서 언제든 직접 내리거나 되살릴 수 있습니다.</p>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<div class="card">
    <h2>대기 중인 신고 <span class="pill"><c:out value="${openReports.size()}" />건</span></h2>
    <c:choose>
        <c:when test="${empty openReports}">
            <p class="muted" style="margin-top:10px;">대기 중인 신고가 없습니다.</p>
        </c:when>
        <c:otherwise>
            <table style="margin-top:10px; width:100%;">
                <tr><th style="text-align:left;">글 제목</th><th>신고자</th><th>사유</th><th>상세</th><th>신고일</th><th></th></tr>
                <c:forEach var="report" items="${openReports}">
                    <tr>
                        <td><a href="${pageContext.request.contextPath}/admin/articles?status=" title="목록에서 찾기"><c:out value="${report.articleTitle}" /></a></td>
                        <td><c:out value="${report.reporterName}" /></td>
                        <td><c:out value="${report.reasonType}" /></td>
                        <td class="muted"><c:out value="${report.detail}" default="—" /></td>
                        <td class="muted"><c:out value="${report.createdAtText}" /></td>
                        <td style="white-space:nowrap;">
                            <form method="post" action="${pageContext.request.contextPath}/admin/articles" class="inline-form"
                                  onsubmit="return confirm('이 글을 내릴까요?');">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="hide">
                                <input type="hidden" name="articleId" value="${report.articleId}">
                                <input type="hidden" name="reason" value="신고 처리: ${report.reasonType}">
                                <button type="submit" class="link-button" style="color:var(--danger);">글 내리기</button>
                            </form>
                            <form method="post" action="${pageContext.request.contextPath}/admin/articles" class="inline-form">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="dismissReport">
                                <input type="hidden" name="reportId" value="${report.id}">
                                <button type="submit" class="link-button">기각</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<div class="card">
    <div class="spread">
        <h2>전체 글 목록 <span class="muted" style="font-weight:400;">(<c:out value="${totalCount}" />건)</span></h2>
        <div class="row" style="gap:6px;">
            <a class="btn secondary${empty statusFilter ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/articles">전체</a>
            <a class="btn secondary${statusFilter == 'PUBLISHED' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/articles?status=PUBLISHED">공개</a>
            <a class="btn secondary${statusFilter == 'HIDDEN' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/articles?status=HIDDEN">내려감</a>
        </div>
    </div>
    <c:choose>
        <c:when test="${empty articles}">
            <p class="muted" style="margin-top:10px;">글이 없습니다.</p>
        </c:when>
        <c:otherwise>
            <table style="margin-top:10px; width:100%;">
                <tr><th style="text-align:left;">제목</th><th>작성자</th><th>상태</th><th>작성일</th><th>조회·하트</th><th></th></tr>
                <c:forEach var="article" items="${articles}">
                    <tr>
                        <td><c:out value="${article.title}" /></td>
                        <td><c:out value="${article.authorName}" /></td>
                        <td>
                            <c:choose>
                                <c:when test="${article.status == 'PUBLISHED'}"><span class="chip chip-teal">공개</span></c:when>
                                <c:when test="${article.status == 'HIDDEN'}"><span class="chip" style="background:var(--danger-bg); color:var(--danger);">내려감</span></c:when>
                                <c:otherwise><span class="chip chip-locked"><c:out value="${article.status}" /></span></c:otherwise>
                            </c:choose>
                            <c:if test="${not empty article.hiddenReason}">
                                <div class="muted" style="font-size:0.78rem; margin-top:2px;"><c:out value="${article.hiddenReason}" /></div>
                            </c:if>
                        </td>
                        <td class="muted"><c:out value="${article.createdAtText}" /></td>
                        <td class="muted"><c:out value="${article.viewCount}" /> · <c:out value="${article.likeCount}" /></td>
                        <td style="white-space:nowrap;">
                            <c:choose>
                                <c:when test="${article.status == 'PUBLISHED'}">
                                    <form method="post" action="${pageContext.request.contextPath}/admin/articles" class="inline-form"
                                          onsubmit="var r = prompt('내리는 사유를 입력하세요'); if (!r) return false; this.reason.value = r; return true;">
                                        <input type="hidden" name="_csrf" value="${csrfToken}">
                                        <input type="hidden" name="action" value="hide">
                                        <input type="hidden" name="articleId" value="${article.id}">
                                        <input type="hidden" name="reason" value="">
                                        <button type="submit" class="link-button" style="color:var(--danger);">내리기</button>
                                    </form>
                                </c:when>
                                <c:when test="${article.status == 'HIDDEN'}">
                                    <form method="post" action="${pageContext.request.contextPath}/admin/articles" class="inline-form">
                                        <input type="hidden" name="_csrf" value="${csrfToken}">
                                        <input type="hidden" name="action" value="restore">
                                        <input type="hidden" name="articleId" value="${article.id}">
                                        <button type="submit" class="link-button">되살리기</button>
                                    </form>
                                </c:when>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
            </table>
            <div class="row" style="gap:8px; margin-top:12px; justify-content:center;">
                <c:if test="${page > 1}">
                    <a class="btn secondary" href="${pageContext.request.contextPath}/admin/articles?status=${statusFilter}&page=${page - 1}">이전</a>
                </c:if>
                <c:if test="${articles.size() == 30}">
                    <a class="btn secondary" href="${pageContext.request.contextPath}/admin/articles?status=${statusFilter}&page=${page + 1}">다음</a>
                </c:if>
            </div>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
