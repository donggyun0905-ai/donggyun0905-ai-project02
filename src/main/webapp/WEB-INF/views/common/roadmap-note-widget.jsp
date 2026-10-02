<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 로드맵 오른쪽 연습장(2026-10-01) — 마음껏 쓰고 저장하면 서류 보관함에 "연습장 노트.txt"로 보관된다.
     데이터(noteText)는 RoadmapServlet이 실어 주고, 저장은 /roadmap-note로 fetch POST한다. --%>
<div class="card rm-note">
    <h2 style="font-size:1rem;">연습장</h2>
    <p class="muted" style="margin:4px 0 8px;">자유롭게 적고 저장하면 서류 보관함에 보관돼요.</p>
    <textarea id="rmNoteText" maxlength="20000" placeholder="여기에 메모하세요"><c:out value="${noteText}" /></textarea>
    <div class="row" style="margin-top:8px; gap:10px; align-items:center;">
        <button type="button" id="rmNoteSave">저장</button>
        <span class="muted" id="rmNoteStatus" style="font-size:0.82rem;"></span>
    </div>
</div>
<script>
(function () {
    var text = document.getElementById('rmNoteText');
    var status = document.getElementById('rmNoteStatus');
    document.getElementById('rmNoteSave').addEventListener('click', function () {
        status.textContent = '저장 중...';
        fetch('${pageContext.request.contextPath}/roadmap-note', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-CSRF-Token': '${csrfToken}'},
            body: 'text=' + encodeURIComponent(text.value)
        }).then(function (r) {
            status.textContent = r.ok ? '저장됐어요' : '저장하지 못했어요';
        }).catch(function () { status.textContent = '저장하지 못했어요'; });
    });
})();
</script>
