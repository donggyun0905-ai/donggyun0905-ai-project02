<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 이력 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:choose>
    <%-- 6-4. 유효하지 않은 공유 링크 --%>
    <c:when test="${not valid}">
        <div class="card empty-state" style="max-width:460px; margin:60px auto;">
            <div class="icon">🔗</div>
            <h2>유효하지 않은 링크입니다</h2>
            <p class="muted">링크가 만료되었거나 지원자가 공유를 멈췄습니다. 이력을 보려면 지원자에게 새 링크를 요청해 주세요.</p>
            <a href="${pageContext.request.contextPath}/">스펙 오디세이 알아보기</a>
        </div>
    </c:when>
    <c:otherwise>
        <div class="spread" style="margin-bottom:16px;">
            <span class="pill">👁 읽기 전용 · 지원자가 공유한 이력</span>
            <a class="btn secondary" href="${pageContext.request.contextPath}/share/compare">비교 목록에 담기</a>
        </div>
        <h1>지원자 이력</h1>
        <p class="muted">지원자가 직접 발급한 링크로 열린 페이지입니다. 지원자가 고른 항목만 보이고, 지원자는 언제든 공유를 멈출 수 있습니다.</p>

        <div class="card">
            <h2>기본 정보</h2>
            <div class="row" style="margin-top:10px;">
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">전공</div><strong>컴퓨터공학과</strong></span>
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">학년</div><strong>4학년</strong></span>
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">희망 직무</div><strong>백엔드 개발자</strong></span>
            </div>
            <p style="margin-top:12px; margin-bottom:0;">
                공개된 항목
                <span class="chip chip-teal">기본 이력</span>
                <span class="chip chip-teal">보유 기술 스택</span>
                <span class="chip chip-teal">성장 잠재력</span>
            </p>
        </div>

        <div class="card">
            <h2>이력 타임라인</h2>
            <table style="margin-top:10px;">
                <tr><td class="muted" style="width:110px;">재학 중</td><td><strong>전공</strong><br>컴퓨터공학과 4학년</td></tr>
                <tr><td class="muted">2026-06-01</td><td><strong>자격증</strong><br>정보처리기능사 취득</td></tr>
                <tr><td class="muted">2026-09-09 ~ 09-23</td><td><strong>프로젝트</strong><br>백엔드 API 서버 미니 프로젝트<br><span class="muted" style="font-size:0.84rem;">Spring Boot와 MySQL로 REST API를 만들고 Docker로 배포까지 해본 프로젝트입니다. 사용 기술: Java, Spring Boot, MySQL, Docker</span></td></tr>
                <tr><td class="muted">2026-09-23</td><td><strong>자격증</strong><br>정보처리산업기사 취득</td></tr>
            </table>
        </div>

        <div class="card">
            <h2>보유 기술 스택</h2>
            <p style="margin-top:10px;">
                <span class="pill">Spring Boot · 초급</span>
                <span class="pill">Docker · 초급</span>
                <span class="pill">MySQL · 초급</span>
                <span class="pill">Git · 중급</span>
            </p>
        </div>

        <div class="card">
            <h2>성장 잠재력</h2>
            <p style="margin-top:10px;">최근 4개월 동안 스펙 완성도가 <strong>44에서 62로</strong> 올랐습니다. 이 기간에 자격증 1개와 프로젝트 1개가 늘었습니다.</p>
            <div class="row" style="margin-top:10px; align-items:flex-end; gap:20px;">
                <div style="text-align:center;"><div>44</div><div style="width:24px; height:30px; background:var(--locked); margin-top:4px;"></div><div class="muted" style="font-size:0.78rem;">6월</div></div>
                <div style="text-align:center;"><div>49</div><div style="width:24px; height:36px; background:var(--locked); margin-top:4px;"></div><div class="muted" style="font-size:0.78rem;">7월</div></div>
                <div style="text-align:center;"><div>55</div><div style="width:24px; height:44px; background:var(--teal); margin-top:4px;"></div><div class="muted" style="font-size:0.78rem;">8월</div></div>
                <div style="text-align:center;"><div>62</div><div style="width:24px; height:52px; background:var(--teal); margin-top:4px;"></div><div class="muted" style="font-size:0.78rem;">9월</div></div>
            </div>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
