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
        <c:set var="link" value="${view.link}" />
        <c:set var="user" value="${view.user}" />
        <div class="spread" style="margin-bottom:16px;">
            <span class="pill">👁 읽기 전용 · 지원자가 공유한 이력</span>
            <a class="btn secondary" href="${pageContext.request.contextPath}/share/compare">비교 목록에 담기</a>
        </div>
        <h1>지원자 이력</h1>
        <p class="muted">지원자가 직접 발급한 링크로 열린 페이지입니다. 지원자가 고른 항목만 보이고, 지원자는 언제든 공유를 멈출 수 있습니다.</p>

        <div class="card">
            <h2>기본 정보</h2>
            <div class="row" style="margin-top:10px;">
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">전공</div><strong>${empty user.major ? '미입력' : user.major}</strong></span>
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">학년</div><strong>${empty user.grade ? '미입력' : user.grade}</strong></span>
                <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">희망 직무</div><strong>${empty view.jobName ? '미설정' : view.jobName}</strong></span>
            </div>
            <p style="margin-top:12px; margin-bottom:0;">
                공개된 항목
                <c:if test="${link.scopeBasic}"><span class="chip chip-teal">기본 이력</span></c:if>
                <c:if test="${link.scopeSkills}"><span class="chip chip-teal">보유 기술 스택</span></c:if>
                <c:if test="${link.scopeGrowth}"><span class="chip chip-teal">성장 잠재력</span></c:if>
            </p>
        </div>

        <c:if test="${link.scopeBasic}">
            <div class="card">
                <h2>이력 타임라인</h2>
                <c:choose>
                    <c:when test="${empty view.timeline}">
                        <p class="muted" style="margin-top:10px;">아직 등록된 이력이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <table style="margin-top:10px;">
                            <c:forEach var="item" items="${view.timeline}">
                                <tr>
                                    <td class="muted" style="width:140px;">${item.dateLabel}</td>
                                    <td>
                                        <strong>${item.type}</strong><br>${item.title}
                                        <c:if test="${not empty item.detail}"><br><span class="muted" style="font-size:0.84rem;">${item.detail}</span></c:if>
                                    </td>
                                </tr>
                            </c:forEach>
                        </table>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <c:if test="${link.scopeSkills}">
            <div class="card">
                <h2>보유 기술 스택</h2>
                <c:choose>
                    <c:when test="${empty view.skills}">
                        <p class="muted" style="margin-top:10px;">등록된 기술이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p style="margin-top:10px;">
                            <c:forEach var="skill" items="${view.skills}">
                                <span class="pill">${skill.name}<c:if test="${not empty skill.proficiency}"> · ${skill.proficiency}</c:if></span>
                            </c:forEach>
                        </p>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <c:if test="${link.scopeGrowth}">
            <div class="card">
                <h2>성장 잠재력</h2>
                <c:choose>
                    <c:when test="${empty view.growthSummary}">
                        <p class="muted" style="margin-top:10px;">아직 비교할 과거 기록이 없습니다 — 스펙 완성도 스냅샷이 하루 1회씩 쌓인 뒤 보여드릴 수 있어요.</p>
                    </c:when>
                    <c:otherwise>
                        <p style="margin-top:10px;">${view.growthSummary.fromDate}부터 ${view.growthSummary.toDate}까지 스펙 완성도가 <strong>${view.growthSummary.fromScore}에서 ${view.growthSummary.toScore}로</strong> 올랐습니다. 이 기간에 자격증 ${view.growthSummary.certDelta}개, 프로젝트 ${view.growthSummary.projectDelta}개, 기술 ${view.growthSummary.skillDelta}개가 늘었습니다.</p>
                        <c:if test="${not empty view.growthSeries}">
                            <div class="row" style="margin-top:10px; align-items:flex-end; gap:20px;">
                                <c:forEach var="point" items="${view.growthSeries}">
                                    <div style="text-align:center;">
                                        <div>${point.score}</div>
                                        <div style="width:24px; height:${point.score}px; max-height:60px; background:var(--teal); margin-top:4px;"></div>
                                        <div class="muted" style="font-size:0.78rem;">${point.monthLabel}</div>
                                    </div>
                                </c:forEach>
                            </div>
                        </c:if>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
