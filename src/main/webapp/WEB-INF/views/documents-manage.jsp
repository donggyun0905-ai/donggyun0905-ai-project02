<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="서류 보관함 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>서류 보관함</h1>
<p class="muted">프로젝트 산출물과 증빙 서류를 한곳에 모아둡니다. 서류를 올리면 등급 점수가 30점씩 쌓입니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="card">
            <h2>서류 올리기</h2>
            <form class="row" style="margin-top:10px; align-items:flex-end;" enctype="multipart/form-data">
                <span style="flex:1;"><label>파일</label><input type="file"></span>
                <span style="flex:1;"><label>연결할 프로젝트 (선택)</label>
                    <select><option>백엔드 API 서버 미니 프로젝트</option><option>연결 안 함</option></select>
                </span>
            </form>
            <p class="muted" style="font-size:0.8rem; margin:8px 0;">PDF, DOCX, TXT, PNG, JPG · 파일당 최대 20MB</p>
            <button>올리기</button>
        </div>

        <div class="card">
            <h2>내 서류 3개</h2>
            <table style="margin-top:10px;">
                <tr><th>파일</th><th>연결된 프로젝트</th><th>크기</th><th>올린 날</th><th>작업</th></tr>
                <tr><td>project-note.txt</td><td>백엔드 API 서버 미니 프로젝트</td><td>4 KB</td><td>2026-09-23</td><td><a href="#">내려받기</a> · <a href="#" style="color:var(--danger);">삭제</a></td></tr>
                <tr><td>API_명세서.pdf</td><td>백엔드 API 서버 미니 프로젝트</td><td>820 KB</td><td>2026-09-24</td><td><a href="#">내려받기</a> · <a href="#" style="color:var(--danger);">삭제</a></td></tr>
                <tr><td>배포_결과_화면.png</td><td class="muted">연결 안 함</td><td>1.2 MB</td><td>2026-09-25</td><td><a href="#">내려받기</a> · <a href="#" style="color:var(--danger);">삭제</a></td></tr>
            </table>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
