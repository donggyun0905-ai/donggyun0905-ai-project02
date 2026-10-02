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
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="confirmJob">
                    <input type="hidden" name="confirmedJobId" value="${pendingJobMatch.job.id}">
                    <input type="hidden" name="email" value="${pendingEmail}">
                    <input type="hidden" name="major" value="${pendingMajor}">
                    <input type="hidden" name="name" value="<c:out value='${pendingPersonalInfo.name}' />">
                    <input type="hidden" name="age" value="${pendingPersonalInfo.age}">
                    <input type="hidden" name="careerStatus" value="${pendingPersonalInfo.careerStatus}">
                    <input type="hidden" name="grade" value="<c:out value='${pendingPersonalInfo.grade}' />">
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
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <%-- 저장에 실패해 다시 그릴 때는 방금 입력한 값(param)을, 아니면 저장된 값을 보여준다 --%>
                <c:set var="statusValue" value="${empty param.careerStatus ? user.careerStatus : param.careerStatus}" />
                <p><label>이름</label><input type="text" name="name" maxlength="50" required value="<c:out value='${empty param.name ? user.name : param.name}' />"></p>
                <p><label>나이</label><input type="number" name="age" min="1" max="120" required value="<c:out value='${empty param.age ? user.age : param.age}' />"></p>
                <p>
                    <label>구분</label>
                    <select name="careerStatus" id="careerStatus" required>
                        <option value="" ${empty statusValue ? 'selected' : ''} disabled>선택해주세요</option>
                        <option value="STUDENT" ${statusValue == 'STUDENT' ? 'selected' : ''}>학생</option>
                        <option value="JOB_SEEKER" ${statusValue == 'JOB_SEEKER' ? 'selected' : ''}>취준생</option>
                        <option value="EMPLOYED" ${statusValue == 'EMPLOYED' ? 'selected' : ''}>직장인</option>
                    </select>
                </p>
                <p id="grade-field"><label>학년</label><input type="text" name="grade" maxlength="20" required placeholder="예) 3학년" value="<c:out value='${empty param.grade ? user.grade : param.grade}' />"></p>
                <p><label>이메일</label><input type="email" name="email" value="${user.email}"></p>
                <p><label>전공</label><input type="text" name="major" value="${user.major}"></p>
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
        <script>
            // 학년은 구분이 학생일 때만 받는다 — 숨긴 칸은 필수 검사와 전송에서도 뺀다
            (function () {
                var careerStatus = document.getElementById('careerStatus');
                var gradeField = document.getElementById('grade-field');
                function toggle() {
                    var student = careerStatus.value === 'STUDENT';
                    gradeField.style.display = student ? '' : 'none';
                    gradeField.querySelector('input').disabled = !student;
                }
                careerStatus.addEventListener('change', toggle);
                toggle();
            })();
        </script>
    </c:otherwise>
</c:choose>

<h2 style="margin-top:24px; margin-bottom:10px;">이력서 · 자소서 <span class="muted" style="font-weight:normal; font-size:0.85rem;">(선택)</span></h2>
<%-- 팀 회의에서 정한 안내 문구(2026-10-01): 이력서·자소서를 선택으로 올려 두면 면접관이 한 화면에서 한눈에 볼 수 있다는 점을 알린다. --%>
<div class="card" style="background:var(--card-bg, #fff); border-left:4px solid var(--teal, #2E7D6B);">
    <p style="margin:0 0 6px;"><strong>💡 이력서와 자소서를 올려 두면 면접관이 한눈에 볼 수 있어 편해요</strong></p>
    <p class="muted" style="margin:0; font-size:0.88rem; line-height:1.6;">
        공유 링크를 만들 때 <strong>이력서 파일</strong>·<strong>자소서 파일</strong>을 함께 골라 두면, 면접관은 링크 하나로 내 기본 이력과 기술 스택부터 이력서·자소서까지
        한 화면에서 확인하고 바로 내려받을 수 있어요. 파일을 따로 주고받거나 여러 군데를 찾아다니지 않아도 되니 면접관에게도 훨씬 수월하고, 내 이야기가 빠짐없이 전해집니다.
        둘 다 <strong>선택</strong>이라 올리지 않아도 가입·분석·로드맵에는 아무 영향이 없고, 개인정보가 들어 있는 파일이라 링크를 만들 때 직접 체크한 경우에만 공개돼요.
    </p>
</div>
<c:if test="${not empty resumeMessage}">
    <p class="error-message"><c:out value="${resumeMessage}" /></p>
</c:if>
<div class="card">
    <h3 style="margin:0 0 8px; font-size:1rem;">이력서 <span class="muted" style="font-weight:normal; font-size:0.8rem;">· 선택</span></h3>
    <c:choose>
        <c:when test="${not empty resume}">
            <p style="margin-top:0;">
                <a href="${pageContext.request.contextPath}/documents/${resume.id}">📎 <c:out value="${resume.originalName}" /></a>
                <span class="muted">· ${resume.createdAt.toLocalDate()}에 올림</span>
            </p>
        </c:when>
        <c:otherwise>
            <p class="muted" style="margin-top:0;">아직 올린 이력서가 없습니다. 안 올려도 괜찮아요.</p>
        </c:otherwise>
    </c:choose>
    <form action="${pageContext.request.contextPath}/profile/resume" method="post" enctype="multipart/form-data" class="row">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="file" name="resume" required accept=".pdf,.doc,.docx,.hwp,.hwpx" style="flex:1;">
        <button type="submit">${empty resume ? '이력서 올리기' : '새 파일로 바꾸기'}</button>
    </form>
    <p class="muted" style="font-size:0.8rem; margin:8px 0 0;">PDF, Word(doc·docx), 한글(hwp·hwpx) 파일을 10MB까지 올릴 수 있습니다. 새 파일을 올리면 이전 이력서는 지워집니다.</p>
    <c:if test="${not empty resume}">
        <form action="${pageContext.request.contextPath}/profile/resume" method="post" class="delete-resume" style="margin-top:8px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="delete">
            <button type="submit" class="link-button">이력서 삭제</button>
        </form>
        <script>
            document.querySelector('.delete-resume').addEventListener('submit', function (event) {
                if (!confirm('이력서를 삭제할까요?')) {
                    event.preventDefault();
                }
            });
        </script>
    </c:if>
</div>

<c:if test="${not empty coverLetterMessage}">
    <p class="error-message"><c:out value="${coverLetterMessage}" /></p>
</c:if>
<div class="card">
    <h3 style="margin:0 0 8px; font-size:1rem;">자소서 <span class="muted" style="font-weight:normal; font-size:0.8rem;">· 선택</span></h3>
    <c:choose>
        <c:when test="${not empty coverLetter}">
            <p style="margin-top:0;">
                <a href="${pageContext.request.contextPath}/documents/${coverLetter.id}">📎 <c:out value="${coverLetter.originalName}" /></a>
                <span class="muted">· ${coverLetter.createdAt.toLocalDate()}에 올림</span>
            </p>
        </c:when>
        <c:otherwise>
            <p class="muted" style="margin-top:0;">아직 올린 자소서가 없습니다. 안 올려도 괜찮아요.</p>
        </c:otherwise>
    </c:choose>
    <form action="${pageContext.request.contextPath}/profile/cover-letter" method="post" enctype="multipart/form-data" class="row">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="file" name="coverLetter" required accept=".pdf,.doc,.docx,.hwp,.hwpx" style="flex:1;">
        <button type="submit">${empty coverLetter ? '자소서 올리기' : '새 파일로 바꾸기'}</button>
    </form>
    <p class="muted" style="font-size:0.8rem; margin:8px 0 0;">PDF, Word(doc·docx), 한글(hwp·hwpx) 파일을 10MB까지 올릴 수 있습니다. 새 파일을 올리면 이전 자소서는 지워집니다.</p>
    <c:if test="${not empty coverLetter}">
        <form action="${pageContext.request.contextPath}/profile/cover-letter" method="post" class="delete-coverLetter" style="margin-top:8px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="delete">
            <button type="submit" class="link-button">자소서 삭제</button>
        </form>
        <script>
            document.querySelector('.delete-coverLetter').addEventListener('submit', function (event) {
                if (!confirm('자소서를 삭제할까요?')) {
                    event.preventDefault();
                }
            });
        </script>
    </c:if>
</div>

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
                        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                    <input type="hidden" name="_csrf" value="${csrfToken}">
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
        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                    <input type="hidden" name="_csrf" value="${csrfToken}">
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
        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                    <input type="hidden" name="_csrf" value="${csrfToken}">
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
        <input type="hidden" name="_csrf" value="${csrfToken}">
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

<%-- FR-101·102(선택) AI 활용 기록 자기 제출 — "이런 식으로 AI를 활용했다"를 직접 적어두는
     최하위 우선순위 선택 기능. 공유 여부는 본인이 항목별로 정한다(NFR-4). --%>
<h2 style="margin-top:24px; margin-bottom:10px;">AI 활용 기록 <span class="muted" style="font-size:0.78rem; font-weight:normal;">(선택)</span></h2>
<p class="muted" style="margin-top:0;">AI 도구를 어떻게 활용했는지 직접 기록해두는 공간입니다. 공유 여부는 항목마다 따로 정할 수 있습니다.</p>
<c:choose>
    <c:when test="${empty aiUsageEntries}">
        <p class="muted">아직 작성한 기록이 없습니다.</p>
    </c:when>
    <c:otherwise>
        <ul class="item-list">
            <c:forEach var="entry" items="${aiUsageEntries}">
                <li>
                    <div class="spread">
                        <strong>${entry.title}</strong>
                        <span class="row" style="gap:8px;">
                            <c:choose>
                                <c:when test="${entry.shared}"><span class="chip chip-teal">공유함</span></c:when>
                                <c:otherwise><span class="chip chip-locked">비공개</span></c:otherwise>
                            </c:choose>
                            <form action="${pageContext.request.contextPath}/profile/ai-usage" method="post" class="inline-form">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="toggleShare">
                                <input type="hidden" name="logId" value="${entry.id}">
                                <input type="hidden" name="shared" value="${entry.shared ? 'false' : 'true'}">
                                <button type="submit" class="link-button">${entry.shared ? '비공개로' : '공유하기'}</button>
                            </form>
                            <form action="${pageContext.request.contextPath}/profile/ai-usage" method="post" class="inline-form">
                                <input type="hidden" name="_csrf" value="${csrfToken}">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="logId" value="${entry.id}">
                                <button type="submit" class="link-button">삭제</button>
                            </form>
                        </span>
                    </div>
                    <c:if test="${not empty entry.description}"><p class="muted" style="margin:6px 0 0; font-size:0.88rem;">${entry.description}</p></c:if>
                </li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>
<div class="card">
    <form action="${pageContext.request.contextPath}/profile/ai-usage" method="post">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <p><input type="text" name="title" placeholder="제목 (예: 프로젝트 버그 디버깅에 ChatGPT 활용)" required></p>
        <p><textarea name="description" placeholder="어떻게 활용했는지 자유롭게 적어주세요"></textarea></p>
        <p><label style="display:inline; width:auto;"><input type="checkbox" name="shared" value="true" style="width:auto;"> 면접관에게 공유 허용</label></p>
        <button type="submit">기록 추가</button>
    </form>
</div>

<div class="card" style="margin-top:28px; border-color:var(--danger);">
    <h2 style="color:var(--danger); margin-bottom:8px;">회원 탈퇴</h2>
    <p class="muted">탈퇴하면 로그인·프로필 조회가 모두 불가능해집니다. 비밀번호를 입력하고 확인해주세요.</p>
    <form action="${pageContext.request.contextPath}/profile/withdraw" method="post"
          onsubmit="return confirm('정말 탈퇴하시겠습니까?');" class="row">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="password" name="password" placeholder="비밀번호" style="width:auto;" required>
        <button type="submit" class="danger">회원 탈퇴</button>
    </form>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
