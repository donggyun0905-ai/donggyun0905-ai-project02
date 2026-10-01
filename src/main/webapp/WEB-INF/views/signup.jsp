<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="회원가입 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<style>
    .consent-box { border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; margin: 14px 0 8px; }
    .consent-box legend { font-size: 0.9rem; font-weight: 700; padding: 0 6px; }
    .consent-text { font-size: 0.82rem; max-height: 150px; overflow-y: auto; background: #fff; border: 1px solid var(--border);
        border-radius: 6px; padding: 8px 12px; margin-bottom: 8px; }
    .consent-text p { margin: 4px 0; }
    .consent-warn { max-height: none; border-color: var(--gold); }
    .consent-check { display: block; width: auto; font-size: 0.88rem; font-weight: 600; margin: 6px 0; }
    .consent-check input { width: auto; margin-right: 6px; }
</style>

<div class="center-box">
    <h1>회원가입</h1>

    <c:if test="${not empty errorMessage}">
        <p class="error-message"><c:out value="${errorMessage}" /></p>
    </c:if>

    <div class="card">
        <form action="${pageContext.request.contextPath}/register" method="post">
            <p>
                <label>가입 유형</label>
                <label style="display:inline; width:auto; margin-right:14px;"><input type="radio" name="userType" value="APPLICANT" style="width:auto;" ${param.userType == 'INTERVIEWER' ? '' : 'checked'}> 지원자</label>
                <label style="display:inline; width:auto;"><input type="radio" name="userType" value="INTERVIEWER" style="width:auto;" ${param.userType == 'INTERVIEWER' ? 'checked' : ''}> 면접관</label>
            </p>
            <p><label>아이디</label><input type="text" name="loginId" required value="<c:out value='${param.loginId}' />"></p>
            <p><label>비밀번호</label><input type="password" name="password" required></p>
            <p><label>이름</label><input type="text" name="name" maxlength="50" required value="<c:out value='${param.name}' />"></p>
            <p><label>이메일 (선택)</label><input type="email" name="email" value="<c:out value='${param.email}' />"></p>
            <div id="applicant-fields">
                <p><label>나이</label><input type="number" name="age" min="1" max="120" required value="<c:out value='${param.age}' />"></p>
                <p>
                    <label>구분</label>
                    <select name="careerStatus" id="careerStatus" required>
                        <option value="STUDENT" ${param.careerStatus == 'STUDENT' ? 'selected' : ''}>학생</option>
                        <option value="JOB_SEEKER" ${param.careerStatus == 'JOB_SEEKER' ? 'selected' : ''}>취준생</option>
                        <option value="EMPLOYED" ${param.careerStatus == 'EMPLOYED' ? 'selected' : ''}>직장인</option>
                    </select>
                </p>
                <p id="grade-field"><label>학년</label><input type="text" name="grade" maxlength="20" required placeholder="예) 3학년" value="<c:out value='${param.grade}' />"></p>
                <p><label>전공</label><input type="text" name="major" value="<c:out value='${param.major}' />"></p>
                <p><label>관심 분야</label><input type="text" name="interestField" value="<c:out value='${param.interestField}' />"></p>
            </div>
            <div id="interviewer-fields">
                <p><label>회사명 (선택)</label><input type="text" name="companyName" maxlength="100" value="<c:out value='${param.companyName}' />"></p>
                <p class="muted" style="font-size:0.82rem;">면접관 계정은 지원자가 보내 준 공유 링크의 이력을 담아 두고 나란히 비교할 수 있습니다. 지원자를 검색하거나 직접 조회할 수는 없습니다.</p>
            </div>
            <fieldset class="consent-box">
                <legend>개인정보 수집·이용 동의</legend>
                <div class="consent-text">
                    <p><strong>수집 항목</strong> 아이디, 비밀번호(암호화 저장), 이름, 이메일(선택), 나이·구분·학년·전공·관심 분야(지원자), 이후 직접 입력하는 스펙·프로젝트·서류</p>
                    <p><strong>이용 목적</strong> 직무 격차 분석, 로드맵·점수 제공, 본인이 만든 공유 링크를 통한 면접관 열람</p>
                    <p><strong>보유 기간</strong> 회원 탈퇴 시까지. 입력·업로드는 모두 본인의 선택이며, 입력하지 않아도 서비스를 쓸 수 있는 항목이 많습니다.</p>
                    <p>동의하지 않으면 가입할 수 없습니다.</p>
                </div>
                <label class="consent-check"><input type="checkbox" name="privacyConsent" value="Y" required> [필수] 개인정보 수집·이용에 동의합니다.</label>
                <div id="interviewer-notice">
                    <div class="consent-text consent-warn">
                        <p><strong>면접관에게 보이는 자료이니 꼭 확인하세요</strong></p>
                        <p>공유 링크를 만들면 이력서·자소서·프로젝트·서류 등 <strong>내가 공개로 고른 항목은 링크를 받은 면접관에게 그대로 보입니다.</strong>
                            무엇을 올리고 무엇을 공개할지는 전적으로 본인의 선택이며, 공개 범위는 링크마다 따로 정하고 언제든 끌 수 있습니다.
                            (기본은 비공개입니다.) 공개 전에 연락처·주민번호 같은 민감한 정보가 파일에 들어 있지 않은지 직접 확인해 주세요.</p>
                    </div>
                    <label class="consent-check"><input type="checkbox" name="visibilityNotice" value="Y" required> [필수] 위 내용을 숙지했습니다.</label>
                </div>
            </fieldset>
            <button type="submit" style="width:100%; margin-top:6px;">가입하기</button>
        </form>
    </div>

    <p><a href="${pageContext.request.contextPath}/login">이미 계정이 있으신가요? 로그인</a></p>
</div>

<script>
    // 가입 유형과 구분에 맞는 입력칸만 보여주고, 숨긴 칸은 필수 검사에서도 뺀다
    (function () {
        var applicantFields = document.getElementById('applicant-fields');
        var interviewerFields = document.getElementById('interviewer-fields');
        var gradeField = document.getElementById('grade-field');
        var careerStatus = document.getElementById('careerStatus');

        function setShown(box, shown) {
            box.style.display = shown ? '' : 'none';
            box.querySelectorAll('input, select').forEach(function (input) { input.disabled = !shown; });
        }
        function toggle() {
            var interviewer = document.querySelector('input[name="userType"]:checked').value === 'INTERVIEWER';
            setShown(applicantFields, !interviewer);
            // 면접관에게 보이는 자료 안내는 자료를 올리는 지원자에게만 해당한다
            setShown(document.getElementById('interviewer-notice'), !interviewer);
            setShown(interviewerFields, interviewer);
            // 학년은 학생일 때만 받는다
            setShown(gradeField, !interviewer && careerStatus.value === 'STUDENT');
        }
        document.querySelectorAll('input[name="userType"]').forEach(function (radio) {
            radio.addEventListener('change', toggle);
        });
        careerStatus.addEventListener('change', toggle);
        toggle();
    })();
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
