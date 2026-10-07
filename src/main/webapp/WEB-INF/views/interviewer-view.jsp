<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 이력 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:choose>
    <%-- 6-4. 유효하지 않은 공유 링크 --%>
    <c:when test="${not valid}">
        <div class="card empty-state" style="max-width:460px; margin:60px auto;">
            <div class="icon"><span class="ic ic-link" aria-hidden="true"></span></div>
            <h2>유효하지 않은 링크입니다</h2>
            <p class="muted">링크가 만료되었거나 지원자가 공유를 멈췄습니다. 이력을 보려면 지원자에게 새 링크를 요청해 주세요.</p>
            <a href="${pageContext.request.contextPath}/">스펙 오디세이 알아보기</a>
        </div>
    </c:when>
    <c:otherwise>
        <div class="spread" style="margin-bottom:16px;">
            <span class="pill"><span class="ic ic-eye" aria-hidden="true"></span> 읽기 전용 · 지원자가 공유한 이력</span>
            <c:choose>
                <c:when test="${interviewer}">
                    <form method="post" action="${pageContext.request.contextPath}/interviewer/shared">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="add">
                        <input type="hidden" name="link" value="<c:out value='${token}' />">
                        <button type="submit" class="secondary">비교 목록에 담기</button>
                    </form>
                </c:when>
                <c:when test="${empty sessionScope.loginUser}">
                    <a class="btn secondary" href="${pageContext.request.contextPath}/login">면접관 로그인 후 비교 목록에 담기</a>
                </c:when>
            </c:choose>
        </div>
        <h1>지원자 이력</h1>
        <p class="muted">지원자가 직접 발급한 링크로 열린 페이지입니다. 지원자가 고른 항목만 보이고, 지원자는 언제든 공유를 멈출 수 있습니다.</p>

        <div class="card">
            <c:if test="${view.scopeBasic}">
                <h2>기본 정보</h2>
                <div class="row" style="margin-top:10px;">
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">이름</div><strong><c:out value="${view.name}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">전공</div><strong><c:out value="${view.major}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">학년</div><strong><c:out value="${view.grade}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">희망 직무</div><strong><c:out value="${view.desiredJobName}" default="미정" /></strong></span>
                </div>
            </c:if>
            <c:if test="${view.scopeAge}">
                <div class="row" style="margin-top:10px;">
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">나이</div><strong><c:choose><c:when test="${empty view.age}">미입력</c:when><c:otherwise>${view.age}세</c:otherwise></c:choose></strong></span>
                </div>
            </c:if>
            <p style="margin-top:12px; margin-bottom:0;">
                공개된 항목
                <c:if test="${view.scopeBasic}"><span class="chip chip-teal">기본 이력</span></c:if>
                <c:if test="${view.scopeSkills}"><span class="chip chip-teal">보유 기술 스택</span></c:if>
                <c:if test="${view.scopeGrowth}"><span class="chip chip-teal">성장 잠재력</span></c:if>
                <c:if test="${view.scopeResume}"><span class="chip chip-teal">이력서 파일</span></c:if>
                <c:if test="${view.scopeCoverLetter}"><span class="chip chip-teal">자소서 파일</span></c:if>
                <c:if test="${view.scopeAge}"><span class="chip chip-teal">나이</span></c:if>
                <c:if test="${view.scopeActivity}"><span class="chip chip-teal">활동 내역</span></c:if>
            </p>
        </div>

        <%-- 이력서·자소서는 한 카드에 모아서 한눈에 보고 바로 내려받게 한다 --%>
        <c:if test="${view.scopeResume || view.scopeCoverLetter}">
            <div class="card">
                <h2>이력서 · 자소서</h2>
                <c:if test="${view.scopeResume}">
                    <c:choose>
                        <c:when test="${empty view.resumeFileName}">
                            <p class="muted" style="margin-top:10px; margin-bottom:0;">이력서 — 지원자가 아직 올리지 않았습니다.</p>
                        </c:when>
                        <c:otherwise>
                            <p style="margin-top:10px; margin-bottom:0;">
                                이력서 · <a href="${pageContext.request.contextPath}/share/${token}/resume" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${view.resumeFileName}" /> 내려받기</a>
                            </p>
                        </c:otherwise>
                    </c:choose>
                </c:if>
                <c:if test="${view.scopeCoverLetter}">
                    <c:choose>
                        <c:when test="${empty view.coverLetterFileName}">
                            <p class="muted" style="margin-top:10px; margin-bottom:0;">자소서 — 지원자가 아직 올리지 않았습니다.</p>
                        </c:when>
                        <c:otherwise>
                            <p style="margin-top:10px; margin-bottom:0;">
                                자소서 · <a href="${pageContext.request.contextPath}/share/${token}/cover-letter" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${view.coverLetterFileName}" /> 내려받기</a>
                            </p>
                        </c:otherwise>
                    </c:choose>
                </c:if>
            </div>
        </c:if>

        <%-- FR-81 이력 타임라인 --%>
        <c:if test="${view.scopeBasic}">
            <div class="card">
                <h2>이력 타임라인</h2>
                <c:choose>
                    <c:when test="${empty view.timeline}">
                        <p class="muted" style="margin-top:10px;">아직 등록된 자격증·어학·수상·프로젝트가 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <table style="margin-top:10px;">
                            <c:forEach var="item" items="${view.timeline}">
                                <tr>
                                    <td class="muted" style="width:190px;"><c:out value="${item.dateText}" /></td>
                                    <td>
                                        <strong><c:out value="${item.typeLabel}" /></strong><br>
                                        <c:out value="${item.title}" />
                                        <c:if test="${not empty item.detail}">
                                            <br><span class="muted" style="font-size:0.84rem;"><c:out value="${item.detail}" /></span>
                                        </c:if>
                                        <c:if test="${not empty item.links}">
                                            <br><span style="font-size:0.84rem;"><span class="ic ic-link" aria-hidden="true"></span>
                                                <c:forEach var="link" items="${item.links}" varStatus="ls"><c:if test="${!ls.first}"> · </c:if><a href="<c:out value='${link.url}' />" target="_blank" rel="noopener noreferrer nofollow"><c:out value="${link.label}" /></a></c:forEach>
                                            </span>
                                        </c:if>
                                        <c:if test="${not empty item.documentId}">
                                            <br><a href="${pageContext.request.contextPath}/share/documents/${token}/${item.documentId}" target="_blank" style="font-size:0.84rem;">증빙 서류 보기</a>
                                        </c:if>
                                    </td>
                                </tr>
                            </c:forEach>
                        </table>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <c:if test="${view.scopeSkills}">
            <div class="card">
                <h2>보유 기술 스택</h2>
                <c:choose>
                    <c:when test="${empty view.skills}">
                        <p class="muted" style="margin-top:10px;">아직 등록된 기술이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p style="margin-top:10px;">
                            <c:forEach var="skill" items="${view.skills}">
                                <span class="pill"><c:out value="${skill}" /></span>
                            </c:forEach>
                        </p>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- 활동 내역 (2026-10-07) — 날짜별 활동량 잔디 + 최근에 무엇을 했는지.
             지원자가 링크에서 "활동 내역"을 켠 경우에만 보인다(SHARE_LINK.scope_activity). --%>
        <c:if test="${view.scopeActivity}">
            <div class="card">
                <h2>활동 내역</h2>
                <c:choose>
                    <c:when test="${empty view.activity or view.activity.empty}">
                        <p class="muted" style="margin-top:10px;">아직 쌓인 활동 기록이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p class="muted" style="margin-top:4px;">
                            <c:out value="${view.activity.rangeText}" /> · 활동한 날 <strong>${view.activity.activeDays}일</strong>
                            · 전체 <strong>${view.activity.totalEvents}회</strong>
                        </p>
                        <div class="act-grid">
                            <c:forEach var="week" items="${view.activity.weeks}" varStatus="w">
                                <div class="act-week">
                                    <span class="act-month"><c:out value="${view.activity.monthLabels[w.index]}" /></span>
                                    <c:forEach var="cell" items="${week}">
                                        <span class="act-cell lv${cell.level}${cell.filler ? ' act-filler' : ''}"
                                              title="<c:out value='${cell.title}' />"></span>
                                    </c:forEach>
                                </div>
                            </c:forEach>
                        </div>
                        <div class="act-legend">
                            <span class="muted">적음</span>
                            <span class="act-cell lv0"></span><span class="act-cell lv1"></span>
                            <span class="act-cell lv2"></span><span class="act-cell lv3"></span>
                            <span class="act-cell lv4"></span>
                            <span class="muted">많음</span>
                        </div>

                        <c:if test="${not empty view.activity.timeline}">
                            <h3 style="margin:18px 0 8px; font-size:1rem;">최근 활동</h3>
                            <table style="width:100%; font-size:0.88rem;">
                                <c:forEach var="entry" items="${view.activity.timeline}">
                                    <tr>
                                        <td class="muted" style="width:140px;"><c:out value="${entry.stamp}" /></td>
                                        <td style="width:150px;"><span class="chip chip-teal"><c:out value="${entry.label}" /></span></td>
                                        <td><c:out value="${entry.detail}" default="" /></td>
                                        <td class="muted" style="width:60px; text-align:right;">+${entry.points}</td>
                                    </tr>
                                </c:forEach>
                            </table>
                        </c:if>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- FR-84 성장 잠재력 --%>
        <c:if test="${view.scopeGrowth}">
            <div class="card">
                <h2>성장 잠재력</h2>
                <c:choose>
                    <c:when test="${empty view.growth}">
                        <p class="muted" style="margin-top:10px;">아직 스펙 완성도 기록이 쌓이지 않았습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p class="muted" style="margin-top:10px;">스펙 완성도(100점 만점)가 시간에 따라 변한 기록입니다.</p>
                        <div class="row" style="margin-top:10px; align-items:flex-end; gap:20px;">
                            <c:forEach var="point" items="${view.growth}">
                                <div style="text-align:center;">
                                    <div>${point.completenessScore}</div>
                                    <div style="width:24px; height:${point.completenessScore * 0.8}px; background:var(--teal); margin:4px auto 0;"></div>
                                    <div class="muted" style="font-size:0.78rem;">${point.snapshotDate}</div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>
    </c:otherwise>
</c:choose>

<style>
    /* 활동 잔디 (2026-10-07) — 주 단위 열을 옆으로 쌓고, 한 열이 월~일 7칸이다 */
    .act-grid { display: flex; gap: 3px; margin-top: 10px; overflow-x: auto; padding-bottom: 4px; }
    .act-week { display: flex; flex-direction: column; gap: 3px; flex-shrink: 0; }
    .act-month { font-size: 0.68rem; color: var(--ink-soft); height: 12px; white-space: nowrap; }
    .act-cell { width: 12px; height: 12px; border-radius: 3px; background: var(--border); display: inline-block; }
    .act-cell.lv1 { background: #cfe3dd; }
    .act-cell.lv2 { background: #9ec9bf; }
    .act-cell.lv3 { background: #5aa493; }
    .act-cell.lv4 { background: var(--teal); }
    .act-cell.act-filler { background: transparent; }
    .act-legend { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 0.78rem; }
    .act-legend .muted { margin: 0 4px; }
</style>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
