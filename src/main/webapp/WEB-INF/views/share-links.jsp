<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="공유 링크 관리 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>공유 링크 관리</h1>
<p class="muted">면접관에게 보여줄 이력을 링크로 공유합니다. 링크를 받은 사람만 로그인 없이 읽기 전용으로 볼 수 있고, 언제든 공유를 멈출 수 있습니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="card">
            <h2>새 링크 만들기</h2>
            <div class="row" style="margin-top:10px;">
                <span style="flex:2;"><label>메모 (나만 보임)</label><input type="text" placeholder="예) A사 백엔드 지원"></span>
                <span style="flex:1;"><label>만료</label><input type="text" value="30일 뒤"></span>
            </div>
            <div class="card" style="background:#fff; margin:12px 0 0;">
                <strong style="font-size:0.88rem;">공개 범위</strong>
                <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" style="width:auto;" checked> <strong>기본 이력</strong> — 전공, 자격증, 프로젝트 타임라인</label></p>
                <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" style="width:auto;" checked> <strong>보유 기술 스택</strong> — 면접관의 적합도 계산에 쓰입니다</label></p>
                <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" style="width:auto;"> <strong>성장 잠재력</strong> — 최근 스펙이 늘어난 속도</label></p>
                <p class="muted" style="font-size:0.78rem; margin:6px 0 0;">격차 분석, 등급과 점수, 미션 기록, 서류, AI 활용 기록은 어떤 경우에도 공유되지 않습니다.</p>
            </div>
            <button style="margin-top:12px;">링크 만들기</button>
        </div>

        <div class="card">
            <h2>내 공유 링크</h2>
            <ul class="item-list" style="margin-top:10px;">
                <li>
                    <div class="spread"><strong>A사 백엔드 지원</strong><span class="chip chip-teal">공유 중</span></div>
                    <p class="muted" style="margin:6px 0; font-size:0.84rem;">만료 2026-10-28 · 공개: 기본 이력, 보유 기술 스택 · 열람 3회 (마지막 2026-09-27)</p>
                    <div class="row"><input type="text" value="spec-odyssey.example/share/[토큰]" readonly style="flex:1;"><button class="secondary">주소 복사</button></div>
                    <div class="row" style="margin-top:8px;"><button class="secondary">공유 중단</button><a href="${pageContext.request.contextPath}/share/demo">면접관 화면 미리보기</a></div>
                </li>
                <li>
                    <div class="spread"><strong>B사 인턴 지원</strong><span class="chip chip-locked">공유 중단됨</span></div>
                    <p class="muted" style="margin:6px 0; font-size:0.84rem;">만료 2026-10-15 · 공개: 기본 이력 · 이 링크로는 지금 아무도 볼 수 없습니다</p>
                    <div class="row"><button class="secondary">다시 공유하기</button><button class="link-button">삭제</button></div>
                </li>
                <li>
                    <div class="spread"><strong>C사 서류 전형</strong><span class="chip chip-danger">만료됨</span></div>
                    <p class="muted" style="margin:6px 0; font-size:0.84rem;">2026-09-20에 만료 · 공개: 기본 이력, 보유 기술 스택, 성장 잠재력</p>
                    <button class="link-button">삭제</button>
                </li>
            </ul>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
