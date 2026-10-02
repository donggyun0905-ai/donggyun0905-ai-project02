<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="스펙 아카이브 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/js/vendor/highlight/github.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/spec-archive.css">
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="me" value="${sessionScope.loginUser.id}" />

<p><a href="${ctx}/spec-archive">← 스펙 아카이브</a></p>

<div class="two-col">
<div class="primary">

<c:choose>
<c:when test="${empty detail}">
    <div class="card sa-empty">글을 찾을 수 없습니다. 지워졌거나 주소가 잘못되었을 수 있습니다.</div>
</c:when>
<c:otherwise>
<c:set var="post" value="${detail.post}" />
<article class="sa-post">
    <c:if test="${not empty archiveMessage}"><p class="sa-flash"><c:out value="${archiveMessage}" /></p></c:if>
    <c:if test="${not empty archiveError}"><p class="error-message"><c:out value="${archiveError}" /></p></c:if>

    <h1><c:out value="${post.title}" /></h1>
    <div class="sa-meta" style="margin-top:8px;">
        <strong><c:out value="${post.authorName}" /></strong>
        <c:if test="${not empty post.authorTitle}"><span class="sa-tier"><c:out value="${post.authorTitle}" /></span></c:if>
        <span>${post.publishedAt.toLocalDate()}</span>
        <span>👁 ${post.viewCount}</span>
    </div>

    <%-- 본문과 사진·영상을 글쓴이가 놓은 순서대로 보여준다 (ArchiveContentCodec.decode).
         글은 c:out으로 이스케이프한 글자 그대로 넣고, 링크만 스크립트(spec-archive.js)가 안전하게 <a>로 바꾼다. --%>
    <div class="sa-content">
        <c:forEach var="seg" items="${detail.segments}">
            <c:choose>
                <c:when test="${seg.codeBlock}">
                    <%-- 코드는 c:out으로 이스케이프한 글자 그대로 넣고, highlight.js가 그 글자로 색만 입힌다 --%>
                    <div class="sa-code">
                        <div class="sa-code-head">
                            <span><c:out value="${seg.codeLanguageLabel}" /></span>
                            <button type="button" class="link-button" data-copy-code>복사</button>
                        </div>
                        <pre><code class="language-${seg.codeLanguage}"><c:out value="${seg.code}" /></code></pre>
                    </div>
                </c:when>
                <c:when test="${not seg.media}">
                    <div class="sa-body" data-linkify><c:out value="${seg.text}" /></div>
                </c:when>
                <c:otherwise>
                    <c:set var="att" value="${seg.attachment}" />
                    <div class="sa-media">
                        <c:choose>
                            <c:when test="${att.uploadedImage}">
                                <img src="${ctx}/spec-archive/image/${att.id}" alt="<c:out value='${att.originalName}' />" loading="lazy">
                            </c:when>
                            <c:when test="${att.linkedImage}">
                                <%-- 외부 이미지 — https만 저장된다. 우리 페이지 주소가 외부로 새지 않게 referrer를 보내지 않는다 --%>
                                <img src="${fn:escapeXml(att.url)}" alt="링크 이미지" loading="lazy" referrerpolicy="no-referrer">
                            </c:when>
                            <c:when test="${att.youtube}">
                                <%-- 원본 링크가 아니라 검증한 영상 ID로만 임베드 주소를 만든다.
                                     referrerpolicy: 사이트 전체 정책(same-origin)이면 Referer가 빠져 유튜브가 "동영상 플레이어 구성 오류(153)"를
                                     내므로, 이 iframe만 출처(도메인)까지 보낸다 — 페이지 경로·쿼리는 보내지 않는다 --%>
                                <div class="sa-video">
                                    <iframe src="https://www.youtube-nocookie.com/embed/${fn:escapeXml(att.embedKey)}" title="유튜브 영상"
                                            loading="lazy" referrerpolicy="strict-origin-when-cross-origin"
                                            allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen" allowfullscreen></iframe>
                                </div>
                            </c:when>
                        </c:choose>
                    </div>
                </c:otherwise>
            </c:choose>
        </c:forEach>
    </div>

    <div class="sa-reactions" id="reactions">
        <form method="post" action="${ctx}/spec-archive/post">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="id" value="${post.id}"><input type="hidden" name="action" value="like">
            <button type="submit" class="sa-react ${detail.liked ? 'on' : ''}" aria-pressed="${detail.liked}">${detail.liked ? '❤️' : '🤍'} ${post.likeCount}</button>
        </form>
        <form method="post" action="${ctx}/spec-archive/post">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="id" value="${post.id}"><input type="hidden" name="action" value="bookmark">
            <button type="submit" class="sa-react bookmark ${detail.bookmarked ? 'on' : ''}" aria-pressed="${detail.bookmarked}">🔖 ${detail.bookmarked ? '북마크됨' : '북마크'} ${post.bookmarkCount}</button>
        </form>
        <span class="muted">💬 ${post.commentCount}</span>
        <c:if test="${detail.mine}">
            <form method="post" action="${ctx}/spec-archive/post" class="spacer" data-confirm="이 글을 지울까요? 지우면 되돌릴 수 없습니다.">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="id" value="${post.id}"><input type="hidden" name="action" value="deletePost">
                <button type="submit" class="danger">글 지우기</button>
            </form>
        </c:if>
    </div>

    <section id="comments">
        <h2 style="font-size:1.05rem;">댓글 ${post.commentCount}</h2>

        <form id="commentForm" class="sa-comment-form" method="post" action="${ctx}/spec-archive/post" style="margin-top:10px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="id" value="${post.id}">
            <input type="hidden" name="action" value="comment">
            <input type="hidden" name="replyTo" id="replyTo" value="">
            <div class="sa-replying" id="replying" hidden>
                <span id="replyingText"></span>
                <button type="button" class="link-button" id="cancelReply">취소</button>
            </div>
            <textarea name="content" id="commentContent" maxlength="${commentMax}" required placeholder="댓글을 남겨 주세요."></textarea>
            <div class="row" style="justify-content:space-between;">
                <span class="sa-count" data-count-for="commentContent" data-max="${commentMax}" style="margin:0;"></span>
                <button type="submit">댓글 달기</button>
            </div>
        </form>

        <c:if test="${empty detail.threads}"><p class="muted">첫 댓글을 남겨 보세요.</p></c:if>

        <c:forEach var="thread" items="${detail.threads}">
            <c:set var="top" value="${thread.comment}" />
            <div class="sa-thread">
                <div class="sa-comment">
                    <c:choose>
                        <c:when test="${top.deleted}"><p class="sa-deleted" style="margin:0;">삭제된 댓글입니다.</p></c:when>
                        <c:otherwise>
                            <div class="sa-comment-head">
                                <strong><c:out value="${top.authorName}" /></strong>
                                <span class="muted">${top.createdAt.toLocalDate()}</span>
                            </div>
                            <div class="sa-comment-text" data-linkify><c:out value="${top.content}" /></div>
                            <div class="sa-comment-actions">
                                <button type="button" class="link-button" data-reply="${top.id}" data-reply-name="<c:out value='${top.authorName}' />">답글 달기</button>
                                <c:if test="${top.userId == me}">
                                    <form method="post" action="${ctx}/spec-archive/post" data-confirm="댓글을 지울까요?">
                                        <input type="hidden" name="_csrf" value="${csrfToken}">
                                        <input type="hidden" name="id" value="${post.id}"><input type="hidden" name="action" value="deleteComment">
                                        <input type="hidden" name="commentId" value="${top.id}">
                                        <button type="submit" class="link-button" style="color:var(--danger);">지우기</button>
                                    </form>
                                </c:if>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </div>

                <c:if test="${not empty thread.replies}">
                    <div class="sa-replies">
                        <c:forEach var="reply" items="${thread.replies}">
                            <div class="sa-comment">
                                <div class="sa-comment-head">
                                    <strong><c:out value="${reply.authorName}" /></strong>
                                    <span class="muted">${reply.createdAt.toLocalDate()}</span>
                                </div>
                                <div class="sa-comment-text"><c:if test="${not empty reply.replyToName}"><span class="sa-mention">@<c:out value="${reply.replyToName}" /></span></c:if><span data-linkify><c:out value="${reply.content}" /></span></div>
                                <div class="sa-comment-actions">
                                    <button type="button" class="link-button" data-reply="${reply.id}" data-reply-name="<c:out value='${reply.authorName}' />">답글 달기</button>
                                    <c:if test="${reply.userId == me}">
                                        <form method="post" action="${ctx}/spec-archive/post" data-confirm="댓글을 지울까요?">
                                            <input type="hidden" name="_csrf" value="${csrfToken}">
                                            <input type="hidden" name="id" value="${post.id}"><input type="hidden" name="action" value="deleteComment">
                                            <input type="hidden" name="commentId" value="${reply.id}">
                                            <button type="submit" class="link-button" style="color:var(--danger);">지우기</button>
                                        </form>
                                    </c:if>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:if>
            </div>
        </c:forEach>
    </section>
</article>
</c:otherwise>
</c:choose>
</div>
<%-- 오늘의 트렌드 기술 (FR-54·55) — 데이터는 TrendWidgetFilter가 목표 직무 기준으로 실어 준다 --%>
<div class="side">
    <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
</div>
</div>

<%-- highlight.js 11.9.0 (BSD-3, js/vendor/highlight/LICENSE) — 코드 블록 색 입히기 --%>
<script src="${ctx}/js/vendor/highlight/highlight.min.js"></script>
<script src="${ctx}/js/spec-archive.js"></script>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
