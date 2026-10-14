<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="팁 쓰기 - 스펙 아카이브" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/js/vendor/highlight/github.min.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/spec-archive.css">
<c:set var="ctx" value="${pageContext.request.contextPath}" />

<%-- 로드맵 전문가 단계에서 들어오면(?stepId=N) 돌아갈 곳과 제목이 바뀐다 (2026-10-14) --%>
<c:choose>
    <c:when test="${not empty expertStepId}">
        <p><a href="${ctx}/roadmap">← 내 로드맵</a></p>
        <h1>기술 설명 글 쓰기</h1>
        <div class="banner" style="margin-bottom:14px;">
            <span>
                <strong><c:out value="${expertSkillName}" /></strong> 전문가 단계예요.
                이 글을 올리면 단계가 완료되고, 스펙 아카이브에 공개됩니다.
                사진·다이어그램·코드 블록을 넣을 수 있어요.
            </span>
        </div>
    </c:when>
    <c:otherwise>
        <p><a href="${ctx}/spec-archive">← 스펙 아카이브</a></p>
        <h1>팁 쓰기</h1>
    </c:otherwise>
</c:choose>

<c:if test="${not empty errorMessage}"><p class="error-message"><c:out value="${errorMessage}" /></p></c:if>

        <%-- 편집기: js/spec-archive-editor.js. 보낼 때 본문(사진 자리에 [[upload:K]])과 사진 파일(image_K)을 채운다 --%>
        <form id="writeForm" class="card sa-form" action="${ctx}/spec-archive/write" method="post" enctype="multipart/form-data" autocomplete="off"
              data-content-max="${contentMax}" data-max-image-mb="${maxImageMb}" data-max-total-mb="${maxTotalMb}">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <%-- 전문가 단계에서 들어왔으면 그 단계 번호를 들고 간다. 저장할 때 서비스가 다시 확인한다 --%>
            <c:if test="${not empty expertStepId}"><input type="hidden" name="stepId" value="${expertStepId}"></c:if>
            <%-- 노션처럼 쓰는 화면이 곧 올라갈 모습이다 — 제목 · 본문 블록(글/사진/영상) --%>
            <div class="ed-page">
                <input type="text" id="title" name="title" class="ed-title" maxlength="${titleMax}" required
                       value="<c:out value='${title}' />" placeholder="제목" aria-label="제목">
                <div class="sa-count" data-count-for="title" data-max="${titleMax}"></div>
                <%-- 무슨 기술에 대한 글인지 — 목록에서 유형별로 거르는 데 쓴다 (2026-10-14).
                     전문가 단계로 들어왔으면 그 단계의 기술로 이미 정해져 있어 묻지 않는다. --%>
                <c:if test="${empty expertStepId}">
                    <p class="sa-skill-pick">
                        <label for="skillId">어떤 기술에 대한 글인가요? <span class="muted">(선택)</span></label>
                        <select id="skillId" name="skillId">
                            <option value="">고르지 않음</option>
                            <c:forEach var="sk" items="${allSkills}">
                                <option value="${sk.id}" ${skillId == sk.id ? 'selected' : ''}><c:out value="${sk.skillName}" /></option>
                            </c:forEach>
                        </select>
                    </p>
                </c:if>

                <div class="ed-toolbar">
                    <button type="button" class="secondary" id="pickImages"><span class="ic ic-camera" aria-hidden="true"></span> 사진 넣기</button>
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
                <button type="submit" id="submitButton">${not empty expertStepId ? '올리고 단계 완료' : '올리기'}</button>
                <a class="btn secondary" href="${not empty expertStepId ? ctx.concat('/roadmap') : ctx.concat('/spec-archive')}">취소</a>
            </div>
        </form>

<%-- highlight.js 11.9.0 (BSD-3, js/vendor/highlight/LICENSE) — 코드 블록 색 입히기 --%>
<script src="${ctx}/js/vendor/highlight/highlight.min.js"></script>
<script src="${ctx}/js/spec-archive.js"></script>
<script src="${ctx}/js/spec-archive-editor.js"></script>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
