<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="내 프로필 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>내 프로필</h1>

<c:if test="${not empty currentTier}">
    <div class="card row" style="gap:20px;">
        <img src="${pageContext.request.contextPath}${tierLogoPath}" alt="${currentTier.tierName}" height="70">
        <p style="margin:0;">
            현재 등급: <strong>${currentTier.tierName}</strong> (${currentTier.titleName})<br>
            누적 점수: <strong>${totalScore}점</strong>
            <c:if test="${not empty currentTier.maxScore}">
                / 다음 등급까지 ${currentTier.maxScore - totalScore + 1}점
            </c:if>
        </p>
    </div>
</c:if>

<c:if test="${not empty errorMessage}">
    <p class="error-message">${errorMessage}</p>
</c:if>

<h2 style="margin-top:24px; margin-bottom:10px;">기본 정보</h2>
<c:choose>
    <%-- 편집 거리로 대신 골라낸 애매한 매칭은 바로 저장하지 않고 여기서 먼저 확인받는다 —
         희망 직무는 격차분석·로드맵을 좌우하는 값이라 잘못 자동교정되면 위험해서다. JOB에
         설명 컬럼이 없어서, 카테고리와 그 직무의 별칭들을 "뭐 하는 직무인지" 힌트로 보여준다. --%>
    <c:when test="${not empty pendingJobMatch}">
        <div class="card">
            <p>입력하신 내용과 정확히 일치하는 직무가 없어서, 가장 비슷한 직무를 찾았습니다. 맞나요?</p>
            <p style="margin:10px 0;">
                <strong>${pendingJobMatch.job.jobName}</strong>
                <c:if test="${not empty pendingJobMatch.job.jobCategory}">
                    <span class="chip chip-teal">${pendingJobMatch.job.jobCategory}</span>
                </c:if>
            </p>
            <c:if test="${not empty pendingJobMatch.aliasNames}">
                <p class="muted">이런 이름으로도 불립니다:
                    <c:forEach var="alias" items="${pendingJobMatch.aliasNames}" varStatus="st">${alias}<c:if test="${!st.last}">, </c:if></c:forEach>
                </p>
            </c:if>
            <div class="row" style="margin-top:14px; gap:10px;">
                <form action="${pageContext.request.contextPath}/profile" method="post">
                    <input type="hidden" name="action" value="confirmJob">
                    <input type="hidden" name="confirmedJobId" value="${pendingJobMatch.job.id}">
                    <input type="hidden" name="email" value="${pendingEmail}">
                    <input type="hidden" name="major" value="${pendingMajor}">
                    <input type="hidden" name="grade" value="${pendingGrade}">
                    <input type="hidden" name="interestField" value="${pendingInterestField}">
                    <button type="submit">예, 이 직무로 저장</button>
                </form>
                <a href="${pageContext.request.contextPath}/profile" class="btn secondary">아니오, 다시 검색</a>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        <div class="card">
            <form action="${pageContext.request.contextPath}/profile" method="post">
                <p><label>이메일</label><input type="email" name="email" value="${user.email}"></p>
                <p><label>전공</label><input type="text" name="major" value="${user.major}"></p>
                <p><label>학년</label><input type="text" name="grade" value="${user.grade}"></p>
                <p><label>관심 분야</label><input type="text" name="interestField" value="${user.interestField}"></p>
                <p>
                    <label>희망 직무</label>
                    <%-- list=datalist: 클릭하면 전체 직무가 드롭박스로 보이고, 입력할수록 그중 일치하는
                         것만 브라우저가 알아서 좁혀준다(JS 없이 HTML5 표준 기능). 정식 명칭뿐 아니라
                         JOB_ALIAS의 별칭도 같이 후보로 넣어서, 다르게 알고 있는 이름으로 쳐도 뜬다 —
                         실제 매칭(별칭 → job_id)은 서버(ProfileService.resolveJobQuery)에서 한다.
                         비워두고 저장하면 "아직 모르겠음"으로 저장된다(별도 라디오 없이 입력칸 하나로 판단). --%>
                    <input type="text" name="desiredJobQuery" list="jobOptions" style="width:auto;"
                           autocomplete="off" placeholder="아직 모르겠으면 비워두세요 (다른 이름으로 알고 있어도 OK)"
                           value="${desiredJobQuery}">
                    <datalist id="jobOptions">
                        <c:forEach var="job" items="${jobs}">
                            <option value="${job.jobName}">
                        </c:forEach>
                        <c:forEach var="alias" items="${jobAliases}">
                            <option value="${alias.aliasName}">
                        </c:forEach>
                    </datalist>
                </p>
                <button type="submit">기본정보 저장</button>
            </form>
        </div>
    </c:otherwise>
</c:choose>

<h2 style="margin-top:24px; margin-bottom:10px;">보유 스펙</h2>
<ul class="item-list">
    <c:forEach var="spec" items="${specs}">
        <li class="spread">
            <span>
                [${spec.specType}] ${spec.title}
                <c:if test="${not empty spec.issuer}"> · ${spec.issuer}</c:if>
                <c:if test="${not empty spec.score}"> · ${spec.score}</c:if>
                <c:if test="${not empty spec.acquiredDate}"> · ${spec.acquiredDate}</c:if>
            </span>
            <span class="row" style="gap:8px;">
                <details class="inline-form">
                    <summary>수정</summary>
                    <form action="${pageContext.request.contextPath}/profile/specs" method="post">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="specId" value="${spec.id}">
                        <p>
                            <select name="specType">
                                <option value="CERT" ${spec.specType == 'CERT' ? 'selected' : ''}>자격증</option>
                                <option value="LANGUAGE" ${spec.specType == 'LANGUAGE' ? 'selected' : ''}>어학</option>
                                <option value="AWARD" ${spec.specType == 'AWARD' ? 'selected' : ''}>수상</option>
                            </select>
                        </p>
                        <p><input type="text" name="title" value="${spec.title}" placeholder="명칭" required></p>
                        <p><input type="text" name="issuer" value="${spec.issuer}" placeholder="발급기관"></p>
                        <p><input type="text" name="score" value="${spec.score}" placeholder="점수(어학 등)"></p>
                        <p><input type="date" name="acquiredDate" value="${spec.acquiredDate}"></p>
                        <button type="submit">저장</button>
                    </form>
                </details>
                <form action="${pageContext.request.contextPath}/profile/specs" method="post" class="inline-form">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="specId" value="${spec.id}">
                    <button type="submit" class="link-button">삭제</button>
                </form>
            </span>
        </li>
    </c:forEach>
</ul>
<div class="card">
    <form action="${pageContext.request.contextPath}/profile/specs" method="post" class="row">
        <input type="hidden" name="action" value="add">
        <select name="specType" style="width:auto;">
            <option value="CERT">자격증</option>
            <option value="LANGUAGE">어학</option>
            <option value="AWARD">수상</option>
        </select>
        <input type="text" name="title" placeholder="명칭" required style="width:auto; flex-grow:1;">
        <input type="text" name="issuer" placeholder="발급기관" style="width:auto;">
        <input type="text" name="score" placeholder="점수(어학 등)" style="width:auto;">
        <input type="date" name="acquiredDate" style="width:auto;">
        <button type="submit">스펙 추가</button>
    </form>
</div>

<h2 style="margin-top:24px; margin-bottom:10px;">프로젝트 · 경험</h2>
<ul class="item-list">
    <c:forEach var="project" items="${projects}">
        <li>
            <strong>${project.title}</strong>
            <span class="muted">(${project.startDate} ~ ${project.endDate})</span><br>
            ${project.description}<br>
            <c:if test="${not empty project.techStack}">기술스택: ${project.techStack}</c:if>
            <c:forEach var="doc" items="${documents}">
                <c:if test="${doc.projectId == project.id}">
                    <br><a href="${pageContext.request.contextPath}/documents/${doc.id}">📎 ${doc.originalName}</a>
                </c:if>
            </c:forEach>
            <div class="row" style="margin-top:8px;">
                <details class="inline-form">
                    <summary>수정</summary>
                    <form action="${pageContext.request.contextPath}/profile/projects" method="post">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="projectId" value="${project.id}">
                        <p><input type="text" name="title" value="${project.title}" placeholder="프로젝트명" required></p>
                        <p><textarea name="description" placeholder="설명">${project.description}</textarea></p>
                        <p><input type="text" name="techStack" value="${project.techStack}" placeholder="사용 기술 (예: Java, Spring, MySQL)"></p>
                        <p class="row">
                            <span style="flex:1;">시작일 <input type="date" name="startDate" value="${project.startDate}"></span>
                            <span style="flex:1;">종료일 <input type="date" name="endDate" value="${project.endDate}"></span>
                        </p>
                        <button type="submit">저장</button>
                    </form>
                </details>
                <form action="${pageContext.request.contextPath}/profile/projects" method="post" class="inline-form">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="projectId" value="${project.id}">
                    <button type="submit" class="link-button">삭제</button>
                </form>
            </div>
        </li>
    </c:forEach>
</ul>
<div class="card">
    <form action="${pageContext.request.contextPath}/profile/projects" method="post">
        <p><input type="text" name="title" placeholder="프로젝트명" required></p>
        <p><textarea name="description" placeholder="설명"></textarea></p>
        <p><input type="text" name="techStack" placeholder="사용 기술 (예: Java, Spring, MySQL)"></p>
        <p class="row">
            <span style="flex:1;">시작일 <input type="date" name="startDate"></span>
            <span style="flex:1;">종료일 <input type="date" name="endDate"></span>
        </p>
        <button type="submit">프로젝트 추가</button>
    </form>
</div>

<h2 style="margin-top:24px; margin-bottom:10px;">보유 기술 스택</h2>
<ul class="item-list">
    <c:forEach var="skill" items="${skills}">
        <li class="spread">
            <span>${skill.rawInput}<c:if test="${not empty skill.proficiency}"> (${skill.proficiency})</c:if></span>
            <span class="row" style="gap:8px;">
                <details class="inline-form">
                    <summary>수정</summary>
                    <form action="${pageContext.request.contextPath}/profile/skills" method="post">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="userSkillId" value="${skill.id}">
                        <p><input type="text" name="rawInput" value="${skill.rawInput}" placeholder="기술명 (예: Python, React)" required></p>
                        <p>
                            <select name="proficiency">
                                <option value="" ${empty skill.proficiency ? 'selected' : ''}>숙련도 선택 안함</option>
                                <option value="BEGINNER" ${skill.proficiency == 'BEGINNER' ? 'selected' : ''}>입문</option>
                                <option value="INTERMEDIATE" ${skill.proficiency == 'INTERMEDIATE' ? 'selected' : ''}>중급</option>
                                <option value="ADVANCED" ${skill.proficiency == 'ADVANCED' ? 'selected' : ''}>고급</option>
                            </select>
                        </p>
                        <button type="submit">저장</button>
                    </form>
                </details>
                <form action="${pageContext.request.contextPath}/profile/skills" method="post" class="inline-form">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="userSkillId" value="${skill.id}">
                    <button type="submit" class="link-button">삭제</button>
                </form>
            </span>
        </li>
    </c:forEach>
</ul>
<div class="card">
    <form action="${pageContext.request.contextPath}/profile/skills" method="post" class="row">
        <input type="text" name="rawInput" placeholder="기술명 (예: Python, React)" required style="width:auto; flex-grow:1;">
        <select name="proficiency" style="width:auto;">
            <option value="">숙련도 선택 안함</option>
            <option value="BEGINNER">입문</option>
            <option value="INTERMEDIATE">중급</option>
            <option value="ADVANCED">고급</option>
        </select>
        <button type="submit">기술 추가</button>
    </form>
</div>

<div class="card" style="margin-top:28px; border-color:var(--danger);">
    <h2 style="color:var(--danger); margin-bottom:8px;">회원 탈퇴</h2>
    <p class="muted">탈퇴하면 로그인·프로필 조회가 모두 불가능해집니다. 비밀번호를 입력하고 확인해주세요.</p>
    <form action="${pageContext.request.contextPath}/profile/withdraw" method="post"
          onsubmit="return confirm('정말 탈퇴하시겠습니까?');" class="row">
        <input type="password" name="password" placeholder="비밀번호" style="width:auto;" required>
        <button type="submit" class="danger">회원 탈퇴</button>
    </form>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
