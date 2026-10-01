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
            <form action="${pageContext.request.contextPath}/share-links" method="post" style="margin-top:10px;">
                <input type="hidden" name="action" value="create">
                <div class="row">
                    <span style="flex:2;"><label>메모 (나만 보임)</label><input type="text" name="label" placeholder="예) A사 백엔드 지원"></span>
                    <span style="flex:1;"><label>만료</label>
                        <select name="expiresInDays">
                            <option value="7">7일 뒤</option>
                            <option value="30" selected>30일 뒤</option>
                            <option value="90">90일 뒤</option>
                            <option value="">만료 없음</option>
                        </select>
                    </span>
                </div>
                <div class="card" style="background:#fff; margin:12px 0 0;">
                    <strong style="font-size:0.88rem;">공개 범위</strong>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeBasic" value="true" style="width:auto;" checked> <strong>기본 이력</strong> — 전공, 자격증, 프로젝트 타임라인</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeSkills" value="true" style="width:auto;" checked> <strong>보유 기술 스택</strong> — 면접관의 적합도 계산에 쓰입니다</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeGrowth" value="true" style="width:auto;"> <strong>성장 잠재력</strong> — 최근 스펙이 늘어난 속도</label></p>
                    <p class="muted" style="font-size:0.78rem; margin:6px 0 0;">격차 분석, 등급과 점수, 미션 기록, 서류, AI 활용 기록은 어떤 경우에도 공유되지 않습니다.</p>
                </div>
                <button type="submit" style="margin-top:12px;">링크 만들기</button>
            </form>
        </div>

        <div class="card">
            <h2>내 공유 링크</h2>
            <c:choose>
                <c:when test="${empty myLinks}">
                    <p class="muted" style="margin-top:10px;">아직 만든 공유 링크가 없습니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="item-list" style="margin-top:10px;">
                        <c:forEach var="v" items="${myLinks}">
                            <c:set var="link" value="${v.link}" />
                            <li>
                                <div class="spread">
                                    <strong>${empty link.label ? '(메모 없음)' : link.label}</strong>
                                    <c:choose>
                                        <c:when test="${v.status == 'ACTIVE'}"><span class="chip chip-teal">공유 중</span></c:when>
                                        <c:when test="${v.status == 'EXPIRED'}"><span class="chip chip-danger">만료됨</span></c:when>
                                        <c:otherwise><span class="chip chip-locked">공유 중단됨</span></c:otherwise>
                                    </c:choose>
                                </div>
                                <p class="muted" style="margin:6px 0; font-size:0.84rem;">
                                    <c:choose>
                                        <c:when test="${empty v.expiresAtDisplay}">만료 없음</c:when>
                                        <c:otherwise>만료 ${v.expiresAtDisplay}</c:otherwise>
                                    </c:choose>
                                    · 공개:
                                    <c:if test="${link.scopeBasic}">기본 이력 </c:if>
                                    <c:if test="${link.scopeSkills}">보유 기술 스택 </c:if>
                                    <c:if test="${link.scopeGrowth}">성장 잠재력 </c:if>
                                    ·
                                    <c:choose>
                                        <c:when test="${v.viewCount == 0}">아직 열람 안 됨</c:when>
                                        <c:otherwise>열람 ${v.viewCount}회 (마지막 ${v.lastViewedAtDisplay})</c:otherwise>
                                    </c:choose>
                                </p>
                                <c:if test="${v.status != 'EXPIRED'}">
                                    <div class="row">
                                        <input type="text" value="${pageContext.request.contextPath}/share/${link.token}" readonly style="flex:1;" onclick="this.select();">
                                        <c:if test="${v.status == 'ACTIVE'}">
                                            <a href="${pageContext.request.contextPath}/share/${link.token}" target="_blank" rel="noopener noreferrer">면접관 화면 미리보기</a>
                                        </c:if>
                                    </div>
                                </c:if>
                                <div class="row" style="margin-top:8px;">
                                    <c:choose>
                                        <c:when test="${v.status == 'ACTIVE'}">
                                            <form action="${pageContext.request.contextPath}/share-links" method="post" class="inline-form">
                                                <input type="hidden" name="action" value="toggle">
                                                <input type="hidden" name="linkId" value="${link.id}">
                                                <input type="hidden" name="active" value="false">
                                                <button type="submit" class="secondary">공유 중단</button>
                                            </form>
                                        </c:when>
                                        <c:when test="${v.status == 'PAUSED'}">
                                            <form action="${pageContext.request.contextPath}/share-links" method="post" class="inline-form">
                                                <input type="hidden" name="action" value="toggle">
                                                <input type="hidden" name="linkId" value="${link.id}">
                                                <input type="hidden" name="active" value="true">
                                                <button type="submit" class="secondary">다시 공유하기</button>
                                            </form>
                                        </c:when>
                                    </c:choose>
                                    <form action="${pageContext.request.contextPath}/share-links" method="post" class="inline-form"
                                          onsubmit="return confirm('이 공유 링크를 삭제할까요? 되돌릴 수 없습니다.');">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="linkId" value="${link.id}">
                                        <button type="submit" class="link-button">삭제</button>
                                    </form>
                                </div>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
