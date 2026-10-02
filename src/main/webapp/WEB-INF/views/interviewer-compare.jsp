<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 비교 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<div class="spread" style="margin-bottom:16px;">
    <h1 style="margin:0;">지원자 비교</h1>
    <c:if test="${not empty company}"><span class="pill"><c:out value="${company}" /></span></c:if>
</div>
<p class="muted">담아 둔 지원자를 나란히 비교합니다. 회사가 요구하는 역량과 가중치를 넣으면 지원자별 적합도를 계산합니다. 이 비교는 지원자들이 공개한 항목만 사용합니다.</p>

<div class="card" style="margin-top:16px;">
    <h2>회사 요구 역량</h2>
    <p class="muted" style="margin-top:4px;">가중치(1~5)가 클수록 적합도 점수에 크게 반영됩니다.</p>
    <c:if test="${not empty errorMessage}">
        <p class="error-message" style="margin-top:10px;"><c:out value="${errorMessage}" /></p>
    </c:if>
    <div style="display:flex; flex-direction:column; gap:8px; margin-top:10px;">
        <c:forEach var="criterion" items="${compare.criteria}">
            <div class="row spread">
                <strong><c:out value="${criterion.skillName}" /></strong>
                <span class="row">
                    <span class="muted">가중치 ${criterion.weight}</span>
                    <form method="post" action="${pageContext.request.contextPath}/interviewer/compare" class="inline-form">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="removeCriterion">
                        <input type="hidden" name="criterionId" value="${criterion.id}">
                        <button type="submit" class="link-button" title="역량 삭제">✕</button>
                    </form>
                </span>
            </div>
        </c:forEach>
        <c:if test="${empty compare.criteria}">
            <p class="muted" style="margin:0;">아직 요구 역량이 없습니다. 아래에서 추가하면 적합도 점수가 계산됩니다.</p>
        </c:if>
    </div>
    <form method="post" action="${pageContext.request.contextPath}/interviewer/compare" class="row" style="margin-top:12px; align-items:flex-end;">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="action" value="addCriterion">
        <span style="flex:2;"><label for="skillName">기술</label>
            <input type="text" id="skillName" name="skillName" list="skill-options" required placeholder="예) Java" value="<c:out value='${param.skillName}' />">
            <datalist id="skill-options">
                <c:forEach var="skill" items="${skills}"><option value="<c:out value='${skill.skillName}' />"></option></c:forEach>
            </datalist>
        </span>
        <span style="flex:1;"><label for="weight">가중치</label>
            <select id="weight" name="weight">
                <option value="1">1</option>
                <option value="2">2</option>
                <option value="3" selected>3</option>
                <option value="4">4</option>
                <option value="5">5</option>
            </select>
        </span>
        <button type="submit">역량 추가</button>
    </form>
    <p class="muted" style="font-size:0.78rem; margin:8px 0 0;">이미 있는 기술을 다시 추가하면 가중치만 바뀝니다.</p>
</div>

<div class="card">
    <h2>나란히 보기</h2>
    <c:choose>
        <c:when test="${empty compare.applicants}">
            <p class="muted" style="margin-top:10px;">아직 담은 지원자가 없습니다. <a href="${pageContext.request.contextPath}/interviewer/shared">공유받은 이력</a>에서 받은 링크를 담아 주세요.</p>
        </c:when>
        <c:otherwise>
            <table style="margin-top:10px;">
                <tr>
                    <th>항목</th>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <th>
                            <c:out value="${applicant.label}" />
                            <c:if test="${applicant.available}">
                                <a href="${pageContext.request.contextPath}/share/${applicant.token}" style="margin-left:6px;">이력 보기</a>
                            </c:if>
                        </th>
                    </c:forEach>
                </tr>
                <tr>
                    <td>전공·학년</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">공유 중단됨</td></c:when>
                            <c:when test="${not applicant.view.scopeBasic}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:otherwise><td><c:out value="${applicant.view.major}" default="미입력" /> <c:out value="${applicant.view.grade}" /></td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
                <tr>
                    <td>자격증</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                            <c:when test="${not applicant.view.scopeBasic}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:otherwise><td><c:out value="${applicant.certText}" /></td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
                <tr>
                    <td>프로젝트</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                            <c:when test="${not applicant.view.scopeBasic}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:otherwise><td>${applicant.view.projectCount}개</td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
                <%-- FR-83 요구 역량별 보유 여부 --%>
                <c:forEach var="criterion" items="${compare.criteria}" varStatus="row">
                    <tr>
                        <td><c:out value="${criterion.skillName}" /> · 가중치 ${criterion.weight}</td>
                        <c:forEach var="applicant" items="${compare.applicants}">
                            <c:choose>
                                <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                                <c:when test="${not applicant.view.scopeSkills}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                                <c:when test="${applicant.matches[row.index]}"><td style="color:var(--teal);">갖춤</td></c:when>
                                <c:otherwise><td style="color:var(--danger);">없음</td></c:otherwise>
                            </c:choose>
                        </c:forEach>
                    </tr>
                </c:forEach>
                <c:if test="${not empty compare.criteria}">
                    <tr style="background:#f1e4c8;">
                        <td><strong>적합도 점수</strong></td>
                        <c:forEach var="applicant" items="${compare.applicants}">
                            <c:choose>
                                <c:when test="${empty applicant.fitScore}"><td class="muted">계산하지 않음</td></c:when>
                                <c:otherwise><td><strong>${applicant.fitScore}점</strong></td></c:otherwise>
                            </c:choose>
                        </c:forEach>
                    </tr>
                </c:if>
                <tr>
                    <td>이력서</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                            <c:when test="${not applicant.view.scopeResume}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:when test="${empty applicant.view.resumeFileName}"><td class="muted">올린 이력서 없음</td></c:when>
                            <c:otherwise><td><a href="${pageContext.request.contextPath}/share/${applicant.token}/resume">📎 내려받기</a></td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
                <tr>
                    <td>자소서</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                            <c:when test="${not applicant.view.scopeCoverLetter}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:when test="${empty applicant.view.coverLetterFileName}"><td class="muted">올린 자소서 없음</td></c:when>
                            <c:otherwise><td><a href="${pageContext.request.contextPath}/share/${applicant.token}/cover-letter">📎 내려받기</a></td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
                <tr>
                    <td>성장 잠재력</td>
                    <c:forEach var="applicant" items="${compare.applicants}">
                        <c:choose>
                            <c:when test="${not applicant.available}"><td class="muted">-</td></c:when>
                            <c:when test="${empty applicant.growthText}"><td class="muted">지원자가 공개하지 않음</td></c:when>
                            <c:otherwise><td><c:out value="${applicant.growthText}" /></td></c:otherwise>
                        </c:choose>
                    </c:forEach>
                </tr>
            </table>
            <c:if test="${not empty compare.criteria}">
                <p class="muted" style="font-size:0.78rem; margin-top:10px; margin-bottom:0;">적합도 = 맞춘 역량의 가중치 합 ÷ 전체 가중치 합(${compare.totalWeight}) × 100. 지원자가 기술 스택을 공개하지 않으면 계산하지 않습니다.</p>
            </c:if>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
