<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="회원가입 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<style>
    .signup-box { max-width: 720px; margin: 32px auto 0; }
    .signup-box .card { padding: 26px 30px; }
    .signup-type { display: flex; gap: 10px; margin-bottom: 6px; }
    .signup-type label { flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px; margin: 0;
        padding: 10px; border: 1px solid var(--border); border-radius: 8px; background: #fff; color: var(--ink);
        font-size: 0.92rem; font-weight: 600; cursor: pointer; }
    .signup-type input { width: auto; margin: 0; }
    .signup-type label:has(input:checked) { border-color: var(--primary); background: var(--card-bg); box-shadow: 0 0 0 3px rgba(139,79,42,0.12); }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px; }
    .form-grid .span-2 { grid-column: 1 / -1; }
    .form-grid p { margin: 8px 0; }
    @media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; } .signup-box .card { padding: 20px; } }

    /* 개인정보 동의 창 — 가입하기를 누르면 뜬다 */
    .consent-dialog { border: none; border-radius: var(--radius); padding: 24px 26px; width: min(520px, 92vw);
        background: var(--card-bg); color: var(--ink); box-shadow: 0 12px 40px rgba(0,0,0,0.25); }
    .consent-dialog::backdrop { background: rgba(0,0,0,0.45); }
    .consent-dialog h2 { margin: 0 0 4px; font-size: 1.1rem; }
    .consent-text { font-size: 0.82rem; max-height: 170px; overflow-y: auto; background: #fff; border: 1px solid var(--border);
        border-radius: 6px; padding: 8px 12px; margin: 10px 0 6px; }
    .consent-text p { margin: 4px 0; }
    .consent-warn { max-height: none; border-color: var(--gold); }
    .consent-check { display: flex; align-items: flex-start; gap: 8px; width: auto; font-size: 0.88rem; font-weight: 600;
        color: var(--ink); margin: 8px 0; cursor: pointer; }
    .consent-check input { width: auto; margin: 3px 0 0; }
    .consent-all { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: #fff; }
    .consent-actions { display: flex; gap: 10px; margin-top: 16px; }
    .consent-actions button { flex: 1; }
    .consent-actions button[disabled] { opacity: 0.45; cursor: not-allowed; transform: none; box-shadow: none; }
</style>

<div class="signup-box">
    <h1>회원가입</h1>

    <c:if test="${not empty errorMessage}">
        <p class="error-message"><c:out value="${errorMessage}" /></p>
    </c:if>

    <div class="card">
        <form action="${pageContext.request.contextPath}/register" method="post" id="signupForm">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <label>가입 유형</label>
            <div class="signup-type">
                <label><input type="radio" name="userType" value="APPLICANT" ${param.userType == 'INTERVIEWER' ? '' : 'checked'}> 지원자</label>
                <label><input type="radio" name="userType" value="INTERVIEWER" ${param.userType == 'INTERVIEWER' ? 'checked' : ''}> 면접관</label>
            </div>

            <div class="form-grid">
                <p><label>아이디</label><input type="text" name="loginId" required value="<c:out value='${param.loginId}' />"></p>
                <p><label>비밀번호</label><input type="password" name="password" required placeholder="8자 이상"></p>
                <p><label>이름</label><input type="text" name="name" maxlength="50" required value="<c:out value='${param.name}' />"></p>
                <p><label>이메일 (선택)</label><input type="email" name="email" value="<c:out value='${param.email}' />"></p>
            </div>
            <div id="applicant-fields" class="form-grid">
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
                <p class="span-2"><label>관심 분야</label><input type="text" name="interestField" placeholder="예) 백엔드, 데이터" value="<c:out value='${param.interestField}' />"></p>
            </div>
            <div id="interviewer-fields">
                <p><label>회사명 (선택)</label><input type="text" name="companyName" maxlength="100" value="<c:out value='${param.companyName}' />"></p>
                <p class="muted" style="font-size:0.82rem;">면접관 계정은 지원자가 보내 준 공유 링크의 이력을 담아 두고 나란히 비교할 수 있습니다. 지원자를 검색하거나 직접 조회할 수는 없습니다.</p>
            </div>

            <button type="button" id="openConsent" style="width:100%; margin-top:10px;">가입하기</button>

            <%-- 동의 항목은 가입하기를 누르면 뜨는 작은 창에서 받는다(2026-10-03 사용자 요청 — 폼이 길고 좁아 보였다).
                 창이 폼 안에 있어서 체크박스 값도 같이 제출되고, 서버(RegisterServlet)도 동의 여부를 다시 확인한다. --%>
            <dialog id="consentDialog" class="consent-dialog" aria-labelledby="consentTitle">
                <h2 id="consentTitle">가입 전에 확인해주세요</h2>
                <p class="muted" style="margin:0; font-size:0.85rem;">아래 항목에 동의하면 바로 가입됩니다.</p>

                <label class="consent-check consent-all"><input type="checkbox" id="consentAll"> 모두 동의합니다</label>

                <div class="consent-text">
                    <p><strong>수집 항목</strong> 아이디, 비밀번호(암호화 저장), 이름, 이메일(선택), 나이·구분·학년·전공·관심 분야(지원자), 이후 직접 입력하는 스펙·프로젝트·서류</p>
                    <p><strong>이용 목적</strong> 직무 격차 분석, 로드맵·점수 제공, 본인이 만든 공유 링크를 통한 면접관 열람</p>
                    <p><strong>보유 기간</strong> 회원 탈퇴 시까지(탈퇴 후 30일 유예 뒤 파기). 입력·업로드는 모두 본인의 선택이며, 입력하지 않아도 서비스를 쓸 수 있는 항목이 많습니다.</p>
                    <p>동의하지 않으면 가입할 수 없습니다.</p>
                </div>
                <label class="consent-check"><input type="checkbox" name="privacyConsent" value="Y" class="consent-required"> [필수] 개인정보 수집·이용에 동의합니다.</label>

                <div id="interviewer-notice">
                    <div class="consent-text consent-warn">
                        <p><strong>면접관에게 보이는 자료이니 꼭 확인하세요</strong></p>
                        <p>공유 링크를 만들면 이력서·자소서·프로젝트·서류 등 <strong>내가 공개로 고른 항목은 링크를 받은 면접관에게 그대로 보입니다.</strong>
                            무엇을 올리고 무엇을 공개할지는 전적으로 본인의 선택이며, 공개 범위는 링크마다 따로 정하고 언제든 끌 수 있습니다.
                            (기본은 비공개입니다.) 공개 전에 연락처·주민번호 같은 민감한 정보가 파일에 들어 있지 않은지 직접 확인해 주세요.</p>
                    </div>
                    <label class="consent-check"><input type="checkbox" name="visibilityNotice" value="Y" class="consent-required"> [필수] 위 내용을 숙지했습니다.</label>
                </div>

                <div class="consent-actions">
                    <button type="button" class="secondary" id="consentCancel">취소</button>
                    <button type="submit" id="consentSubmit" disabled>동의하고 가입하기</button>
                </div>
            </dialog>
        </form>
    </div>

    <p><a href="${pageContext.request.contextPath}/login">이미 계정이 있으신가요? 로그인</a></p>
</div>

<script>
    (function () {
        var form = document.getElementById('signupForm');
        var applicantFields = document.getElementById('applicant-fields');
        var interviewerFields = document.getElementById('interviewer-fields');
        var gradeField = document.getElementById('grade-field');
        var careerStatus = document.getElementById('careerStatus');
        var dialog = document.getElementById('consentDialog');
        var consentAll = document.getElementById('consentAll');
        var submitButton = document.getElementById('consentSubmit');

        // 가입 유형과 구분에 맞는 입력칸만 보여주고, 숨긴 칸은 필수 검사·제출에서도 뺀다
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
            refreshConsent();
        }

        function requiredBoxes() {
            return Array.prototype.filter.call(dialog.querySelectorAll('.consent-required'), function (box) { return !box.disabled; });
        }
        // 필수 항목을 모두 체크해야 "동의하고 가입하기"가 눌린다
        function refreshConsent() {
            var boxes = requiredBoxes();
            var allChecked = boxes.every(function (box) { return box.checked; });
            submitButton.disabled = !allChecked;
            consentAll.checked = allChecked && boxes.length > 0;
        }

        consentAll.addEventListener('change', function () {
            requiredBoxes().forEach(function (box) { box.checked = consentAll.checked; });
            refreshConsent();
        });
        dialog.querySelectorAll('.consent-required').forEach(function (box) { box.addEventListener('change', refreshConsent); });

        // 가입하기 — 입력칸을 먼저 확인하고, 문제가 없을 때만 동의 창을 띄운다
        document.getElementById('openConsent').addEventListener('click', function () {
            if (!form.reportValidity()) { return; }
            refreshConsent();
            dialog.showModal();
        });
        document.getElementById('consentCancel').addEventListener('click', function () { dialog.close(); });

        document.querySelectorAll('input[name="userType"]').forEach(function (radio) { radio.addEventListener('change', toggle); });
        careerStatus.addEventListener('change', toggle);
        toggle();
    })();
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
