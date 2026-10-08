<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="관리자 · 기준 데이터 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="reference" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<nav class="admin-tabs" style="margin-bottom:18px;">
    <a href="${pageContext.request.contextPath}/admin/reference?tab=jobs" class="${tab == 'jobs' ? 'active' : ''}">직무</a>
    <a href="${pageContext.request.contextPath}/admin/reference?tab=skills" class="${tab == 'skills' ? 'active' : ''}">기술</a>
    <a href="${pageContext.request.contextPath}/admin/reference?tab=certifications" class="${tab == 'certifications' ? 'active' : ''}">자격증</a>
    <a href="${pageContext.request.contextPath}/admin/reference?tab=aliases" class="${tab == 'aliases' ? 'active' : ''}">기술 별칭</a>
    <a href="${pageContext.request.contextPath}/admin/reference?tab=requirements" class="${tab == 'requirements' ? 'active' : ''}">직무별 요구 기술</a>
    <a href="${pageContext.request.contextPath}/admin/reference?tab=prerequisites" class="${tab == 'prerequisites' ? 'active' : ''}">기술 선수관계</a>
</nav>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<c:if test="${tab == 'jobs'}">
    <div class="card">
        <h2>직무 추가</h2>
        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px; flex-wrap:wrap;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="tab" value="jobs">
            <input type="hidden" name="action" value="saveJob">
            <input type="text" name="jobName" placeholder="직무명" required style="flex:2; min-width:160px;">
            <input type="text" name="jobCategory" placeholder="분야(예: 백엔드)" style="flex:1; min-width:140px;">
            <label class="row" style="width:auto; gap:4px;"><input type="checkbox" name="popular"> 인기 직무</label>
            <button type="submit">추가</button>
        </form>
    </div>
    <div class="card">
        <h2>직무 목록 (${jobs.size()}개)</h2>
        <table style="margin-top:10px; width:100%;">
            <tr><th style="text-align:left;">직무명</th><th>분야</th><th>인기</th><th></th></tr>
            <c:forEach var="job" items="${jobs}">
                <tr>
                    <td>
                        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="tab" value="jobs">
                            <input type="hidden" name="action" value="saveJob">
                            <input type="hidden" name="id" value="${job.id}">
                            <input type="text" name="jobName" value="<c:out value='${job.jobName}' />" style="flex:2;">
                            <input type="text" name="jobCategory" value="<c:out value='${job.jobCategory}' />" style="flex:1;">
                            <label class="row" style="width:auto; gap:4px;"><input type="checkbox" name="popular" ${job.popular ? 'checked' : ''}> 인기</label>
                            <button type="submit" class="secondary">저장</button>
                        </form>
                    </td>
                    <td colspan="3"></td>
                </tr>
            </c:forEach>
        </table>
    </div>
</c:if>

<c:if test="${tab == 'skills'}">
    <div class="card">
        <h2>기술 추가</h2>
        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="tab" value="skills">
            <input type="hidden" name="action" value="saveSkill">
            <input type="text" name="skillName" placeholder="기술명" required style="flex:2;">
            <input type="text" name="category" placeholder="분야(예: 언어, 프레임워크)" style="flex:1;">
            <button type="submit">추가</button>
        </form>
        <p class="muted" style="margin-top:8px; margin-bottom:0;">새로 추가한 기술은 시맨틱(임베딩) 매칭 대상이 아닙니다 — 이름이 정확히 일치하거나 별칭에 등록된 경우만 매칭됩니다.</p>
    </div>
    <div class="card">
        <h2>기술 목록 (${skills.size()}개)</h2>
        <table style="margin-top:10px; width:100%;">
            <tr><th style="text-align:left;">기술명</th><th>분야</th></tr>
            <c:forEach var="skill" items="${skills}">
                <tr>
                    <td>
                        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="tab" value="skills">
                            <input type="hidden" name="action" value="saveSkill">
                            <input type="hidden" name="id" value="${skill.id}">
                            <input type="text" name="skillName" value="<c:out value='${skill.skillName}' />" style="flex:2;">
                            <input type="text" name="category" value="${skill.category}" style="flex:1;">
                            <button type="submit" class="secondary">저장</button>
                        </form>
                    </td>
                    <td></td>
                </tr>
            </c:forEach>
        </table>
    </div>
</c:if>

<c:if test="${tab == 'certifications'}">
    <div class="card">
        <h2>자격증 추가</h2>
        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px; flex-wrap:wrap;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="tab" value="certifications">
            <input type="hidden" name="action" value="saveCertification">
            <input type="text" name="certName" placeholder="자격증명" required style="flex:2; min-width:160px;">
            <input type="text" name="issuer" placeholder="발급 기관" style="flex:1; min-width:120px;">
            <input type="text" name="jobCategory" placeholder="분야" style="flex:1; min-width:100px;">
            <input type="number" name="difficultyLevel" placeholder="난이도" min="1" max="5" style="width:90px;">
            <button type="submit">추가</button>
        </form>
    </div>
    <div class="card">
        <h2>자격증 목록 (${certifications.size()}개)</h2>
        <table style="margin-top:10px; width:100%;">
            <tr><th style="text-align:left;">자격증명</th><th>발급기관</th><th>분야</th><th>난이도</th><th></th></tr>
            <c:forEach var="cert" items="${certifications}">
                <tr>
                    <td colspan="5">
                        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="flex-wrap:wrap;">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="tab" value="certifications">
                            <input type="hidden" name="action" value="saveCertification">
                            <input type="hidden" name="id" value="${cert.id}">
                            <input type="text" name="certName" value="<c:out value='${cert.certName}' />" style="flex:2; min-width:160px;">
                            <input type="text" name="issuer" value="<c:out value='${cert.issuer}' />" style="flex:1; min-width:120px;">
                            <input type="text" name="jobCategory" value="<c:out value='${cert.jobCategory}' />" style="flex:1; min-width:100px;">
                            <input type="number" name="difficultyLevel" value="${cert.difficultyLevel}" min="1" max="5" style="width:90px;">
                            <button type="submit" class="secondary">저장</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </div>
</c:if>

<c:if test="${tab == 'aliases'}">
    <div class="card">
        <h2>별칭 추가</h2>
        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="tab" value="aliases">
            <input type="hidden" name="action" value="addAlias">
            <select name="skillId" required style="flex:1;">
                <option value="">기술 선택</option>
                <c:forEach var="skill" items="${allSkills}">
                    <option value="${skill.id}"><c:out value="${skill.skillName}" /></option>
                </c:forEach>
            </select>
            <input type="text" name="aliasName" placeholder="별칭(한글 표기·줄임말)" required style="flex:1;">
            <button type="submit">추가</button>
        </form>
    </div>
    <div class="card">
        <h2>별칭 목록 (${aliases.size()}개)</h2>
        <table style="margin-top:10px; width:100%;">
            <tr><th style="text-align:left;">별칭</th><th>종류</th><th></th></tr>
            <c:forEach var="alias" items="${aliases}">
                <tr>
                    <td><c:out value="${alias.aliasName}" /></td>
                    <td class="muted"><c:out value="${alias.matchType}" /></td>
                    <td>
                        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="inline-form"
                              onsubmit="return confirm('이 별칭을 지울까요?');">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="tab" value="aliases">
                            <input type="hidden" name="action" value="deleteAlias">
                            <input type="hidden" name="id" value="${alias.id}">
                            <button type="submit" class="link-button" style="color:var(--danger);">삭제</button>
                        </form>
                    </td>
                </tr>
            </c:forEach>
        </table>
    </div>
</c:if>

<c:if test="${tab == 'prerequisites'}">
    <div class="card">
        <h2>선수관계 추가</h2>
        <p class="muted" style="margin:6px 0 0; font-size:0.86rem;">
            "A를 하기 전에 B"를 등록하면 로드맵이 그 순서를 지킵니다(위상 정렬). 예) Spring 전에 Java, Docker 전에 Linux.<br>
            순환이 되는 관계(A 전에 B인데 B 전에 A)는 저장되지 않습니다 — 로드맵 순서를 정할 수 없게 되기 때문입니다.
        </p>
        <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="tab" value="prerequisites">
            <input type="hidden" name="action" value="addPrerequisite">
            <select name="skillId" required style="flex:1;">
                <option value="">이 기술을 하기 전에</option>
                <c:forEach var="skill" items="${allSkills}">
                    <option value="${skill.id}"><c:out value="${skill.skillName}" /></option>
                </c:forEach>
            </select>
            <select name="prereqSkillId" required style="flex:1;">
                <option value="">먼저 할 기술</option>
                <c:forEach var="skill" items="${allSkills}">
                    <option value="${skill.id}"><c:out value="${skill.skillName}" /></option>
                </c:forEach>
            </select>
            <button type="submit">추가</button>
        </form>
    </div>
    <div class="card">
        <h2>선수관계 목록 (${prerequisites.size()}개)</h2>
        <c:choose>
            <c:when test="${empty prerequisites}">
                <p class="muted" style="margin-top:10px;">아직 등록된 선수관계가 없습니다. 없으면 로드맵은 격차 분석이 매긴 중요도 순서를 그대로 씁니다.</p>
            </c:when>
            <c:otherwise>
                <table style="margin-top:10px; width:100%;">
                    <tr><th style="text-align:left;">먼저 할 기술</th><th style="text-align:left;">그다음 기술</th><th></th></tr>
                    <c:forEach var="pre" items="${prerequisites}">
                        <tr>
                            <td><c:out value="${pre.prereqSkillName}" /></td>
                            <td><c:out value="${pre.skillName}" /></td>
                            <td>
                                <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="inline-form"
                                      onsubmit="return confirm('이 선수관계를 지울까요?');">
                                    <input type="hidden" name="_csrf" value="${csrfToken}">
                                    <input type="hidden" name="tab" value="prerequisites">
                                    <input type="hidden" name="action" value="deletePrerequisite">
                                    <input type="hidden" name="id" value="${pre.id}">
                                    <button type="submit" class="link-button" style="color:var(--danger);">삭제</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                </table>
            </c:otherwise>
        </c:choose>
    </div>
</c:if>

<c:if test="${tab == 'requirements'}">
    <div class="card">
        <h2>직무 선택</h2>
        <form method="get" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px;">
            <input type="hidden" name="tab" value="requirements">
            <select name="jobId" onchange="this.form.submit()" style="flex:1;">
                <option value="">직무 선택</option>
                <c:forEach var="job" items="${allJobs}">
                    <option value="${job.id}" ${selectedJobId == job.id ? 'selected' : ''}><c:out value="${job.jobName}" /></option>
                </c:forEach>
            </select>
            <noscript><button type="submit">조회</button></noscript>
        </form>
    </div>

    <c:if test="${not empty selectedJobId}">
        <div class="card">
            <h2>요구 기술 추가</h2>
            <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row" style="margin-top:10px;">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="tab" value="requirements">
                <input type="hidden" name="action" value="addRequirement">
                <input type="hidden" name="jobId" value="${selectedJobId}">
                <select name="skillId" required style="flex:1;">
                    <option value="">기술 선택</option>
                    <c:forEach var="skill" items="${allSkills}">
                        <option value="${skill.id}"><c:out value="${skill.skillName}" /></option>
                    </c:forEach>
                </select>
                <select name="importance" style="width:120px;">
                    <option value="REQUIRED">필수</option>
                    <option value="PREFERRED">우대</option>
                </select>
                <input type="text" name="requiredLevel" placeholder="수준(선택)" style="width:120px;">
                <button type="submit">추가</button>
            </form>
        </div>
        <div class="card">
            <h2>요구 기술 목록 (${requirements.size()}개)</h2>
            <table style="margin-top:10px; width:100%;">
                <tr><th style="text-align:left;">기술</th><th>중요도</th><th>수준</th><th>출처</th><th></th></tr>
                <c:forEach var="req" items="${requirements}">
                    <tr>
                        <td>
                            <c:forEach var="skill" items="${allSkills}">
                                <c:if test="${skill.id == req.skillId}"><c:out value="${skill.skillName}" /></c:if>
                            </c:forEach>
                        </td>
                        <td colspan="4">
                            <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="row">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="tab" value="requirements">
                                <input type="hidden" name="action" value="updateRequirement">
                                <input type="hidden" name="id" value="${req.id}">
                                <input type="hidden" name="jobId" value="${selectedJobId}">
                                <select name="importance" style="width:120px;">
                                    <option value="REQUIRED" ${req.importance == 'REQUIRED' ? 'selected' : ''}>필수</option>
                                    <option value="PREFERRED" ${req.importance == 'PREFERRED' ? 'selected' : ''}>우대</option>
                                </select>
                                <input type="text" name="requiredLevel" value="${req.requiredLevel}" style="width:120px;">
                                <span class="muted"><c:out value="${req.source}" /></span>
                                <button type="submit" class="secondary">저장</button>
                            </form>
                            <form method="post" action="${pageContext.request.contextPath}/admin/reference" class="inline-form"
                                  onsubmit="return confirm('이 요구 기술을 지울까요?');">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="tab" value="requirements">
                                <input type="hidden" name="action" value="deleteRequirement">
                                <input type="hidden" name="id" value="${req.id}">
                                <input type="hidden" name="jobId" value="${selectedJobId}">
                                <button type="submit" class="link-button" style="color:var(--danger);">삭제</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </table>
        </div>
    </c:if>
</c:if>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
