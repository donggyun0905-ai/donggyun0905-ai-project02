<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="스펙 아카이브 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/spec-archive.css?v=20261012">
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<div class="sa-head">
    <div>
        <h1>스펙 아카이브</h1>
        <p class="muted">${writerTitles} 티어 선배들이 남긴 취업 준비 팁 모음입니다. 하트로 응원하고, 북마크로 모아 두세요.</p>
    </div>
    <%-- 글쓰기는 누구나 (2026-10-14). 등급으로 막는 대신 작성자가 무엇을 증명했는지 보여 준다 --%>
    <a class="btn" href="${ctx}/spec-archive/write"><span class="ic ic-pencil" aria-hidden="true"></span> 팁 쓰기</a>
</div>

<c:if test="${not empty archiveMessage}"><p class="sa-flash" style="margin-top:14px;"><c:out value="${archiveMessage}" /></p></c:if>
<c:if test="${not empty archiveError}"><p class="error-message" style="margin-top:14px;"><c:out value="${archiveError}" /></p></c:if>

<div class="two-col" style="margin-top:4px;">
<div class="primary sa-list-area">

<div class="sa-toolbar">
    <nav class="sa-tabs" aria-label="보기">
        <a href="${ctx}/spec-archive" class="${view == 'all' ? 'active' : ''}">전체</a>
        <a href="${ctx}/spec-archive?view=bookmarks" class="${view == 'bookmarks' ? 'active' : ''}"><span class="ic ic-bookmark" aria-hidden="true"></span> 내 북마크</a>
    </nav>
    <%-- 내 북마크에서는 정렬·검색을 쓰지 않지만 자리는 그대로 둔다 — 전체 ↔ 내 북마크를 오갈 때 목록 위치가 흔들리지 않게 --%>
    <div class="sa-tools ${view == 'all' ? '' : 'is-off'}" ${view == 'all' ? '' : 'inert aria-hidden="true"'}>
        <nav class="sa-sort" aria-label="정렬">
            <a href="${ctx}/spec-archive${empty keyword ? '' : '?q='.concat(keyword)}" class="${sort == 'latest' ? 'active' : ''}">최신순</a>
            <a href="${ctx}/spec-archive?sort=popular${empty keyword ? '' : '&q='.concat(keyword)}" class="${sort == 'popular' ? 'active' : ''}">하트순</a>
        </nav>
        <%-- 제목·본문 검색 (FULLTEXT ngram, 2026-10-08). GET이라 결과 주소를 공유·새로고침할 수 있다 --%>
        <form method="get" action="${ctx}/spec-archive" class="row" style="gap:6px; margin-top:8px;">
            <c:if test="${sort == 'popular'}"><input type="hidden" name="sort" value="popular"></c:if>
            <c:if test="${not empty category}"><input type="hidden" name="category" value="<c:out value='${category}' />"></c:if>
            <input type="search" name="q" value="<c:out value='${keyword}' />" placeholder="제목·본문 검색" style="flex:1;">
            <button type="submit" class="secondary">검색</button>
            <c:if test="${not empty keyword}">
                <a class="btn secondary" href="${ctx}/spec-archive">전체 보기</a>
            </c:if>
        </form>
        <%-- 기술 유형으로 거르기 (2026-10-14). 글이 있는 유형만, 많은 순으로 나온다.
             기술을 고르지 않고 쓴 글은 "기타"로 모인다. 정렬·검색어는 그대로 들고 간다. --%>
        <c:if test="${not empty categories}">
            <nav class="sa-cats" aria-label="기술 유형">
                <%-- 유형 이름에 공백이 있다("프로그래밍 언어") — c:url + c:param 이 주소에 맞게 바꿔 준다 --%>
                <c:url var="allUrl" value="/spec-archive">
                    <c:if test="${sort == 'popular'}"><c:param name="sort" value="popular" /></c:if>
                    <c:if test="${not empty keyword}"><c:param name="q" value="${keyword}" /></c:if>
                </c:url>
                <a href="<c:out value='${allUrl}' />" class="${empty category ? 'active' : ''}">전체</a>
                <c:forEach var="cat" items="${categories}">
                    <c:url var="catUrl" value="/spec-archive">
                        <c:if test="${sort == 'popular'}"><c:param name="sort" value="popular" /></c:if>
                        <c:if test="${not empty keyword}"><c:param name="q" value="${keyword}" /></c:if>
                        <c:param name="category" value="${cat.name}" />
                    </c:url>
                    <a href="<c:out value='${catUrl}' />" class="${category == cat.name ? 'active' : ''}"><c:out value="${cat.name}" /> <span>${cat.count}</span></a>
                </c:forEach>
            </nav>
        </c:if>
    </div>
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
                <c:otherwise>아직 올라온 팁이 없습니다. 첫 번째 팁을 남겨 주세요!</c:otherwise>
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
                        <c:if test="${not empty post.skillName}"><span class="sa-skill"><c:out value="${post.skillName}" /></span></c:if>
                        <span><c:out value="${post.authorName}" /></span>
                        <c:if test="${not empty post.authorTitle}"><span class="sa-tier"><c:out value="${post.authorTitle}" /></span></c:if>
                        <%-- 로드맵에서 전문가 단계까지 끝낸 기술 — 목록은 자리가 좁아 2개까지만 (2026-10-14) --%>
                        <c:forEach var="skill" items="${post.authorExpertSkills}" varStatus="s">
                            <c:if test="${s.index < 2}"><span class="sa-expert"><c:out value="${skill}" /> 전문가</span></c:if>
                        </c:forEach>
                        <c:if test="${fn:length(post.authorExpertSkills) > 2}"><span class="sa-expert sa-expert-more">+${fn:length(post.authorExpertSkills) - 2}</span></c:if>
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
            <%-- 유형 이름의 공백·한글을 주소에 맞게 바꾼다 (c:url 로 한 번 만들어 꼬리만 뗀다) --%>
            <c:url var="catOnly" value="/x"><c:param name="category" value="${category}" /></c:url>
            <c:set var="encodedCategory" value="${fn:substringAfter(catOnly, 'category=')}" />
            <c:set var="base" value="${ctx}/spec-archive?${view == 'bookmarks' ? 'view=bookmarks&' : (sort == 'popular' ? 'sort=popular&' : '')}${empty keyword ? '' : 'q='.concat(keyword).concat('&')}${empty category ? '' : 'category='.concat(encodedCategory).concat('&')}" />
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
