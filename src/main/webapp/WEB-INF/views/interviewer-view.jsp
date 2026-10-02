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
            <p style="margin-top:12px; margin-bottom:0;">
                공개된 항목
                <c:if test="${view.scopeBasic}"><span class="chip chip-teal">기본 이력</span></c:if>
                <c:if test="${view.scopeSkills}"><span class="chip chip-teal">보유 기술 스택</span></c:if>
                <c:if test="${view.scopeGrowth}"><span class="chip chip-teal">성장 잠재력</span></c:if>
                <c:if test="${view.scopeResume}"><span class="chip chip-teal">이력서 파일</span></c:if>
                <c:if test="${view.scopeCoverLetter}"><span class="chip chip-teal">자소서 파일</span></c:if>
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
                                이력서 · <a href="${pageContext.request.contextPath}/share/${token}/resume">📎 <c:out value="${view.resumeFileName}" /> 내려받기</a>
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
                                자소서 · <a href="${pageContext.request.contextPath}/share/${token}/cover-letter">📎 <c:out value="${view.coverLetterFileName}" /> 내려받기</a>
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
                                            <br><span style="font-size:0.84rem;">🔗
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

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
