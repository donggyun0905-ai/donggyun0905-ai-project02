<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="팁 쓰기 - 스펙 아카이브" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/js/vendor/highlight/github.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/spec-archive.css">
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<p><a href="${ctx}/spec-archive">← 스펙 아카이브</a></p>
<h1>팁 쓰기</h1>

<c:choose>
    <c:when test="${not canWrite}">
        <div class="card sa-empty" style="margin-top:16px;">
            🔒 글쓰기는 <strong>${writerTitles}</strong> 티어부터 할 수 있습니다.<br>
            미션과 로드맵으로 점수를 쌓아 티어를 올려 보세요. 읽기·댓글·하트·북마크는 지금도 할 수 있어요.
        </div>
    </c:when>
    <c:otherwise>
        <c:if test="${not empty errorMessage}"><p class="error-message"><c:out value="${errorMessage}" /></p></c:if>

        <%-- 편집기: js/spec-archive-editor.js. 보낼 때 본문(사진 자리에 [[upload:K]])과 사진 파일(image_K)을 채운다 --%>
        <form id="writeForm" class="card sa-form" action="${ctx}/spec-archive/write" method="post" enctype="multipart/form-data" autocomplete="off"
              data-content-max="${contentMax}" data-max-image-mb="${maxImageMb}" data-max-total-mb="${maxTotalMb}">
            <%-- 노션처럼 쓰는 화면이 곧 올라갈 모습이다 — 제목 · 본문 블록(글/사진/영상) --%>
            <div class="ed-page">
                <input type="text" id="title" name="title" class="ed-title" maxlength="${titleMax}" required
                       value="<c:out value='${title}' />" placeholder="제목" aria-label="제목">
                <div class="sa-count" data-count-for="title" data-max="${titleMax}"></div>

                <div class="ed-toolbar">
                    <button type="button" class="secondary" id="pickImages">📷 사진 넣기</button>
                    <button type="button" class="secondary" id="insertCode">&lt;/&gt; 코드</button>
                    <input type="file" id="imagePicker" accept="image/png,image/jpeg,image/gif,image/webp" multiple hidden>
                    <span class="muted ed-hint">사진은 <strong>Ctrl+V</strong>·끌어다 놓기 · 유튜브·이미지 링크는 한 줄에 붙여넣으면 바로 영상·사진 ·
                        <strong>```</strong> + Enter로 코드 · 블록 왼쪽 <strong>⠿</strong>를 끌어 옮기기 · <strong>Ctrl+Z</strong> 되돌리기 / <strong>Ctrl+Y</strong> 다시 하기</span>
                </div>
                <%-- 코드 블록 언어 목록 (SpecArchiveRules.CODE_LANGUAGES) — 편집기가 블록마다 복제해 쓴다 --%>
                <template id="codeLanguageOptions">
                    <c:forEach var="lang" items="${codeLanguages}"><option value="${lang.key}"><c:out value="${lang.value}" /></option></c:forEach>
                </template>

                <div class="ed-editor" id="editor" aria-label="본문 편집기"></div>

                <div class="ed-status">
                    <span id="imageStatus" class="muted"></span>
                    <span class="sa-count" id="contentCount"></span>
                </div>
                <p id="editorNotice" class="error-message" role="status" hidden style="margin:8px 0 0;"></p>

                <%-- 서버로 보내는 본문 — 보내기 직전에 편집기가 채운다. 비워 두는 이유: 브라우저가 뒤로 가기 때 이 칸의
                     예전 값을 되살리면 오류 나기 전 영상·링크가 다시 올라간다 --%>
                <textarea name="content" id="contentField" hidden autocomplete="off"></textarea>
                <%-- 편집기 처음 내용은 서버가 준 값에서만 읽는다 (입력 오류로 다시 그린 화면일 때만 들어 있다).
                     c:out으로 이스케이프되어 template 밖으로 나갈 수 없다 --%>
                <template id="initialContent"><c:out value="${content}" /></template>
                <div id="fileFields" hidden></div>
            </div>

            <div class="row">
                <button type="submit" id="submitButton">올리기</button>
                <a class="btn secondary" href="${ctx}/spec-archive">취소</a>
            </div>
        </form>
    </c:otherwise>
</c:choose>

<%-- highlight.js 11.9.0 (BSD-3, js/vendor/highlight/LICENSE) — 코드 블록 색 입히기 --%>
<script src="${ctx}/js/vendor/highlight/highlight.min.js"></script>
<script src="${ctx}/js/spec-archive.js"></script>
<script src="${ctx}/js/spec-archive-editor.js"></script>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
