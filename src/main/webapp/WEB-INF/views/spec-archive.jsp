<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="스펙 아카이브 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/spec-archive.css">
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<div class="sa-head">
    <div>
        <h1>스펙 아카이브</h1>
        <p class="muted">${writerTitles} 티어 선배들이 남긴 취업 준비 팁 모음입니다. 하트로 응원하고, 북마크로 모아 두세요.</p>
    </div>
    <c:choose>
        <c:when test="${canWrite}"><a class="btn" href="${ctx}/spec-archive/write"><span class="ic ic-pencil" aria-hidden="true"></span> 팁 쓰기</a></c:when>
        <c:otherwise><div class="sa-lock"><span class="ic ic-lock" aria-hidden="true"></span> 글쓰기는 <strong>${writerTitles}</strong> 티어부터 열립니다. 읽기·댓글·하트·북마크는 누구나 할 수 있어요.</div></c:otherwise>
    </c:choose>
</div>

<c:if test="${not empty archiveMessage}"><p class="sa-flash" style="margin-top:14px;"><c:out value="${archiveMessage}" /></p></c:if>
<c:if test="${not empty archiveError}"><p class="error-message" style="margin-top:14px;"><c:out value="${archiveError}" /></p></c:if>

<div class="two-col" style="margin-top:4px;">
<div class="primary">

<div class="sa-toolbar">
    <nav class="sa-tabs" aria-label="보기">
        <a href="${ctx}/spec-archive" class="${view == 'all' ? 'active' : ''}">전체</a>
        <a href="${ctx}/spec-archive?view=bookmarks" class="${view == 'bookmarks' ? 'active' : ''}"><span class="ic ic-bookmark" aria-hidden="true"></span> 내 북마크</a>
    </nav>
    <c:if test="${view == 'all'}">
        <nav class="sa-sort" aria-label="정렬">
            <a href="${ctx}/spec-archive${empty keyword ? '' : '?q='.concat(keyword)}" class="${sort == 'latest' ? 'active' : ''}">최신순</a>
            <a href="${ctx}/spec-archive?sort=popular${empty keyword ? '' : '&q='.concat(keyword)}" class="${sort == 'popular' ? 'active' : ''}">하트순</a>
        </nav>
        <%-- 제목·본문 검색 (FULLTEXT ngram, 2026-10-08). GET이라 결과 주소를 공유·새로고침할 수 있다 --%>
        <form method="get" action="${ctx}/spec-archive" class="row" style="gap:6px; margin-top:8px;">
            <c:if test="${sort == 'popular'}"><input type="hidden" name="sort" value="popular"></c:if>
            <input type="search" name="q" value="<c:out value='${keyword}' />" placeholder="제목·본문 검색" style="flex:1;">
            <button type="submit" class="secondary">검색</button>
            <c:if test="${not empty keyword}">
                <a class="btn secondary" href="${ctx}/spec-archive">전체 보기</a>
            </c:if>
        </form>
    </c:if>
</div>

<c:if test="${not empty keyword}">
    <p class="muted" style="margin:10px 0 0;">
        "<c:out value="${keyword}" />" 검색 결과 <strong>${listPage.total}</strong>건
        <c:if test="${listPage.total == 0}"> — 다른 낱말로 찾아보세요. 두 글자 이상이면 글 중간에 있는 말도 찾습니다.</c:if>
    </p>
</c:if>

<c:choose>
    <c:when test="${empty listPage.posts}">
        <div class="card sa-empty">
            <c:choose>
                <c:when test="${view == 'bookmarks'}">아직 북마크한 글이 없습니다. 마음에 드는 팁에서 북마크를 눌러 보세요.</c:when>
                <c:otherwise>아직 올라온 팁이 없습니다.<c:if test="${canWrite}"> 첫 번째 팁을 남겨 주세요!</c:if></c:otherwise>
            </c:choose>
        </div>
    </c:when>
    <c:otherwise>
        <div class="sa-list">
            <c:forEach var="post" items="${listPage.posts}">
                <a class="sa-item" href="${ctx}/spec-archive/post?id=${post.id}">
                    <h3><c:out value="${post.title}" /></h3>
                    <p class="sa-preview"><c:out value="${post.previewText}" /></p>
                    <div class="sa-meta">
                        <span><c:out value="${post.authorName}" /></span>
                        <c:if test="${not empty post.authorTitle}"><span class="sa-tier"><c:out value="${post.authorTitle}" /></span></c:if>
                        <span>${post.publishedAt.toLocalDate()}</span>
                        <span class="sa-stats">
                            <span title="하트"><span class="ic ic-heart" aria-hidden="true"></span> ${post.likeCount}</span>
                            <span title="댓글"><span class="ic ic-message" aria-hidden="true"></span> ${post.commentCount}</span>
                            <span title="북마크"><span class="ic ic-bookmark" aria-hidden="true"></span> ${post.bookmarkCount}</span>
                            <span title="조회"><span class="ic ic-eye" aria-hidden="true"></span> ${post.viewCount}</span>
                        </span>
                    </div>
                </a>
            </c:forEach>
        </div>

        <c:if test="${listPage.totalPages > 1}">
            <c:set var="base" value="${ctx}/spec-archive?${view == 'bookmarks' ? 'view=bookmarks&' : (sort == 'popular' ? 'sort=popular&' : '')}${empty keyword ? '' : 'q='.concat(keyword).concat('&')}" />
            <div class="sa-pager">
                <c:if test="${listPage.hasPrev}"><a class="btn secondary" href="${base}page=${listPage.page - 1}">이전</a></c:if>
                <span class="muted">${listPage.page} / ${listPage.totalPages}</span>
                <c:if test="${listPage.hasNext}"><a class="btn secondary" href="${base}page=${listPage.page + 1}">다음</a></c:if>
            </div>
        </c:if>
    </c:otherwise>
</c:choose>

</div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
