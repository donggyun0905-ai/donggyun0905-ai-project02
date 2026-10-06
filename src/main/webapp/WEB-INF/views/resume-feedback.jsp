<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="자소서·이력서 첨삭 - 스펙 오디세이" scope="request" />
<c:set var="mainFull" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<%-- FR-91 · 92 · 112
     왼쪽: 내 글 (첨삭에서 적용한 부분을 초록 형광펜으로 표시) / 오른쪽: 원문 위에 제안을 − 빼는 부분 · + 넣는 부분으로 겹쳐 보여주고,
     바뀐 단어 묶음을 하나씩 눌러 적용한다. 두 칸 모두 안쪽 스크롤 없이 내용 길이만큼 늘어난다(페이지 스크롤 하나로 본다).
     사용자 입력과 LLM 출력은 c:out(서버) 또는 textContent(스크립트)로만 화면에 넣는다. --%>
<style>
    .rf-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 20px; margin-top: 16px; align-items: start; }
    .rf-layout .card { margin: 0; }

    /* 내 글은 화면에 붙어서 스크롤을 따라온다 — 오른쪽 비교를 내려 보면서 고쳐진 글을 계속 볼 수 있게.
       칸 높이는 화면에 맞추고, 글이 길면 칸 안에서만 스크롤된다. */
    .rf-input { position: sticky; top: 16px; height: calc(100vh - 32px); min-height: 28rem; box-sizing: border-box;
                display: flex; flex-direction: column; }
    .rf-input form { flex: 1; min-height: 0; display: flex; flex-direction: column; }
    .rf-input .rf-editor { flex: 1; min-height: 10rem; }
    @media (max-width: 960px) {
        .rf-layout { grid-template-columns: minmax(0, 1fr); }
        .rf-input { position: static; height: auto; min-height: 0; }
        .rf-input .rf-editor { flex: none; height: 22rem; }
    }
    .rf-panel-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }

    /* 색 — GitHub Desktop처럼 삭제 빨강, 추가 초록 */
    .rf-layout { --add-bg: #dafbe1; --add-strong: #aceebb; --add-fg: #116329; --add-line: #2da44e;
                 --del-bg: #ffebe9; --del-strong: #ffcecb; --del-fg: #82071e; }

    /* 내 글: 투명한 textarea 뒤에 같은 글을 깔아 적용한 부분만 형광펜을 칠한다. 두 겹의 글꼴·여백이 완전히 같아야 겹친다. */
    .rf-editor { position: relative; margin-top: 4px; }
    .rf-editor textarea, .rf-backdrop {
        display: block; width: 100%; height: 100%; box-sizing: border-box; margin: 0;
        padding: 12px 14px; border: 1px solid var(--border); border-radius: var(--radius);
        font-family: inherit; font-size: 0.95rem; line-height: 1.8; letter-spacing: normal;
        white-space: pre-wrap; overflow-wrap: anywhere; word-break: keep-all;
        /* 스크롤바 자리를 두 겹 모두 같은 폭으로 잡아 둬야 줄바꿈 위치가 같아져 형광펜이 글자와 맞는다 */
        scrollbar-gutter: stable;
    }
    .rf-backdrop { position: absolute; inset: 0; color: transparent; background: #fff; border-color: transparent; overflow-y: hidden; pointer-events: none; }
    .rf-backdrop mark { color: transparent; background: var(--add-strong); border-radius: 3px; box-shadow: 0 0 0 1px var(--add-line); }
    .rf-editor textarea { position: relative; background: transparent; min-height: 0; resize: none; overflow-y: auto; }
    .rf-input .count { font-size: 0.82rem; color: var(--ink-soft); margin-top: 4px; display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
    .rf-mark-legend::before { content: ""; display: inline-block; width: 12px; height: 10px; border-radius: 2px; margin-right: 4px; vertical-align: -1px; background: #aceebb; box-shadow: 0 0 0 1px #2da44e; }

    /* 첨삭 비교 */
    .rf-summary { background: var(--card-bg); border: 1px solid var(--border); border-radius: var(--radius); padding: 10px 14px; margin-top: 10px; }
    .rf-keywords { margin-top: 10px; }
    .rf-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 14px; }
    .rf-toolbar .progress { color: var(--ink-soft); font-size: 0.86rem; margin-right: auto; }
    .rf-legend { display: flex; flex-wrap: wrap; gap: 14px; font-size: 0.8rem; color: var(--ink-soft); margin-top: 8px; }
    .rf-legend span::before { content: ""; display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 4px; vertical-align: -1px; }
    .rf-legend .l-del::before { background: var(--del-strong); }
    .rf-legend .l-add::before { background: var(--add-strong); }
    .rf-legend .l-click::before { border: 1px dashed var(--add-line); }
    .rf-doc { border: 1px solid var(--border); border-radius: var(--radius); background: #fff; padding: 12px 14px; margin-top: 8px;
              white-space: pre-wrap; overflow-wrap: anywhere; word-break: keep-all; line-height: 1.8; font-size: 0.95rem; }
    .rf-hunk { margin: 8px 0; border: 1px solid var(--border); border-left-width: 4px; border-radius: 6px; overflow: hidden; white-space: normal; }
    .rf-hunk.none { border-left-color: var(--gold); }
    .rf-hunk.some { border-left-color: #8fd19e; }
    .rf-hunk.all { border-left-color: var(--add-line); }
    .rf-line { display: flex; gap: 8px; padding: 4px 10px; white-space: pre-wrap; }
    .rf-line .sign { font-family: Consolas, monospace; font-weight: 700; flex-shrink: 0; width: 1ch; }
    .rf-line.del { background: var(--del-bg); color: var(--del-fg); }
    .rf-line.add { background: var(--add-bg); color: var(--add-fg); }
    .rf-chunk { cursor: pointer; border-radius: 3px; padding: 0 1px; text-decoration: none; }
    .rf-chunk:focus-visible { outline: 2px solid var(--teal); outline-offset: 1px; }
    .rf-line.del .rf-chunk { background: var(--del-strong); }
    .rf-line.del .rf-chunk.applied { text-decoration: line-through; opacity: 0.7; }
    .rf-line.add .rf-chunk { box-shadow: 0 0 0 1px var(--add-line) inset; background: transparent; border-bottom: 1px dashed var(--add-line); }
    .rf-line.add .rf-chunk:hover { background: var(--add-bg); }
    .rf-line.add .rf-chunk.applied { background: var(--add-strong); box-shadow: none; border-bottom: none; font-weight: 600; }
    .rf-line.add .rf-chunk.applied::after { content: " ✓"; font-size: 0.8em; }
    .rf-hunk-foot { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; justify-content: space-between; padding: 6px 10px; background: var(--card-bg); }
    .rf-hunk-foot .reason { color: var(--ink-soft); font-size: 0.84rem; flex: 1 1 220px; }
    .rf-hunk-foot .actions { display: flex; gap: 6px; align-items: center; }
    .rf-hunk-foot button { padding: 4px 10px; font-size: 0.84rem; }
    .rf-hunk-foot .count { font-size: 0.8rem; color: var(--ink-soft); }
    .rf-notice { font-size: 0.86rem; margin-top: 8px; }

    /* 자리표시 [어떤 문제를 겪었는지] — 비었으면 노란 칸, 채우면 그 글자가 들어간다 */
    .rf-slot { background: #fff3c4; color: #7a5a00; border-bottom: 1px dashed var(--gold); border-radius: 3px; padding: 0 2px; }
    .rf-slot.filled { background: transparent; color: inherit; border-bottom: 1px solid var(--add-line); font-weight: 600; }
    /* 채워 넣기 칸 — "어떤 문제를 겪었는지: [입력칸]" */
    .rf-fill { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 6px 10px; align-items: center;
               padding: 10px 12px; background: #fff; border-top: 1px dashed var(--border); }
    .rf-fill-title { grid-column: 1 / -1; font-size: 0.8rem; font-weight: 600; color: var(--ink-soft); }
    .rf-fill label { font-size: 0.86rem; color: var(--ink); text-align: right; }
    .rf-fill input { width: 100%; box-sizing: border-box; padding: 6px 10px; border: 1px solid var(--border); border-radius: 6px;
                     font: inherit; font-size: 0.9rem; background: #fffdf5; }
    .rf-fill input:focus { outline: none; border-color: var(--add-line); box-shadow: 0 0 0 3px rgba(45, 164, 78, 0.15); background: #fff; }
    @media (max-width: 600px) { .rf-fill { grid-template-columns: minmax(0, 1fr); } .rf-fill label { text-align: left; } }
</style>

<h1>자소서·이력서 첨삭</h1>
<p class="muted">왼쪽에 글을 넣고 첨삭을 받으면, 오른쪽에서 고칠 곳을 <strong style="color:#82071e;">빼는 부분</strong>·<strong style="color:#116329;">넣는 부분</strong>으로 비교해 보고 바뀐 단어를 눌러 하나씩 적용할 수 있습니다. 대괄호 [ ] 자리는 직접 채우세요.</p>

<div class="rf-layout">
    <%-- 왼쪽: 내 글 --%>
    <div class="card rf-input">
        <h2>내 글</h2>
        <c:if test="${not empty errorMessage}">
            <p class="error-message"><c:out value="${errorMessage}" /></p>
        </c:if>
        <form id="feedbackForm" action="${pageContext.request.contextPath}/resume-feedback" method="post">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <div class="row" style="margin-top:10px;">
                <span style="flex:1;">
                    <label for="jobId">목표 직무</label>
                    <select id="jobId" name="jobId" required>
                        <option value="">직무 선택</option>
                        <c:forEach var="job" items="${jobs}">
                            <option value="${job.id}" ${job.id == selectedJobId ? 'selected' : ''}><c:out value="${job.jobName}" /></option>
                        </c:forEach>
                    </select>
                </span>
                <span style="flex:1;">
                    <label for="docType">글 종류</label>
                    <select id="docType" name="docType">
                        <c:forEach var="type" items="${docTypes}">
                            <option value="${type.name}" ${type.name == selectedDocType ? 'selected' : ''}>${type.label}</option>
                        </c:forEach>
                    </select>
                </span>
            </div>
            <p style="margin:12px 0 0;"><label for="content">내용</label></p>
            <div class="rf-editor">
                <div class="rf-backdrop" id="backdrop" aria-hidden="true"></div>
                <textarea id="content" name="content" maxlength="${maxLength}" required
                          placeholder="자기소개서 문항 답변이나 이력서 경력·프로젝트 설명을 붙여넣으세요."><c:out value="${content}" /></textarea>
            </div>
            <div class="count">
                <span class="rf-mark-legend">첨삭에서 적용한 부분</span>
                <span><span id="charCount">0</span> / ${maxLength}자</span>
            </div>
            <div class="row" style="margin-top:10px; gap:8px;">
                <button id="submitButton" type="submit">AI 첨삭 받기</button>
                <button id="copyButton" type="button" class="secondary">글 복사</button>
            </div>
            <p id="copyNotice" class="muted rf-notice" role="status"></p>
        </form>
    </div>

    <%-- 오른쪽: 첨삭 비교 --%>
    <div class="card rf-diff">
        <div class="rf-panel-head"><h2>첨삭 비교</h2><span class="pill">AI 제안 · 참고용</span></div>
        <c:choose>
            <c:when test="${empty feedback}">
                <p class="muted" style="margin-top:10px;">왼쪽에 글을 넣고 "AI 첨삭 받기"를 누르면, 여기에 고칠 곳이 원문 위에 표시됩니다.</p>
                <div class="rf-legend"><span class="l-del">빼는 부분</span><span class="l-add">넣는 부분</span></div>
            </c:when>
            <c:otherwise>
                <div class="rf-summary"><strong>총평</strong> <c:out value="${feedback.summary}" /></div>

                <c:if test="${not empty feedback.keywords}">
                    <div class="rf-keywords">
                        <p style="margin:0 0 6px;">
                            <strong>함께 쓰면 좋은 키워드</strong>
                            <span class="muted" style="font-size:0.84rem;">— <c:out value="${feedbackJob.jobName}" /> 요구 기술 중 글에 아직 없는 것</span>
                            <c:if test="${feedback.anyEstimated}"><span class="pill">예시적 추정</span></c:if>
                        </p>
                        <c:forEach var="kw" items="${feedback.keywords}">
                            <span class="chip chip-teal"><c:out value="${kw.name}" /></span>
                        </c:forEach>
                    </div>
                </c:if>

                <div class="rf-toolbar">
                    <span class="progress" id="progress"></span>
                    <button type="button" class="secondary" id="applyAll">전부 적용</button>
                    <button type="button" class="secondary" id="resetAll">처음으로</button>
                </div>
                <div class="rf-legend">
                    <span class="l-del">빼는 부분</span><span class="l-add">넣는 부분</span><span class="l-click">눌러서 하나씩 적용</span>
                </div>
                <p id="syncNotice" class="error-message rf-notice" role="status" hidden></p>

                <div class="rf-doc" id="diffDoc" aria-live="polite"></div>
                <c:if test="${empty feedback.items}">
                    <p class="muted" style="margin-top:10px;">따로 고칠 구절을 찾지 못했습니다. 총평을 참고해 보세요.</p>
                </c:if>

                <%-- 비교 화면 데이터. Gson이 < > & 를 < 등으로 바꿔 써서 script 태그를 벗어날 수 없다. --%>
                <script type="application/json" id="feedback-data">${feedbackJson}</script>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/resume-feedback.js"></script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
