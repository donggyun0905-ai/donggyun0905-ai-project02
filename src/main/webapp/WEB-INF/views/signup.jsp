<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="회원가입 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

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
