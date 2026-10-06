<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="회원가입 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<%-- 3단계 가입(2026-10-03 사용자 요청): ① 기본 정보 → ② 추가 정보 → ③ 동의. 화살표를 누르면 상자가 옆으로 넘어간다.
     세 단계가 모두 한 폼 안에 있어 마지막에 한 번에 제출되고, 서버(RegisterServlet)가 모든 값을 다시 검사한다. --%>
<style>
    .signup-box { max-width: 560px; margin: 32px auto 0; }
    .signup-box .card { padding: 24px 28px 22px; overflow: hidden; }
    .wizard-steps { display: flex; align-items: center; gap: 8px; margin-bottom: 18px; font-size: 0.82rem; color: var(--ink-soft); }
    .wizard-steps .dot { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px;
        border-radius: 50%; border: 1px solid var(--border); background: #fff; font-weight: 700; font-size: 0.75rem; }
    .wizard-steps .bar { flex: 1; height: 2px; background: var(--border); border-radius: 2px; }
    .wizard-steps .on { color: var(--ink); font-weight: 700; }
    .wizard-steps .on .dot { background: var(--primary); border-color: var(--primary); color: #fff; }
    .wizard-steps .done .dot { background: var(--teal-bg); border-color: var(--teal); color: var(--teal); }

    .wizard-viewport { overflow: hidden; transition: height 0.35s ease; }
    .wizard-track { display: flex; width: 300%; align-items: flex-start; transition: transform 0.38s cubic-bezier(.4,.1,.2,1); }
    .wizard-step { width: calc(100% / 3); padding: 2px 2px 4px; box-sizing: border-box; }
    .wizard-step h2 { margin: 0 0 4px; font-size: 1.05rem; }
    .wizard-step > .muted { margin: 0 0 10px; font-size: 0.84rem; }
    .wizard-step p { margin: 10px 0; }
    @media (prefers-reduced-motion: reduce) { .wizard-viewport, .wizard-track { transition: none; } }

    .signup-type { display: flex; gap: 10px; }
    .signup-type label { flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px; margin: 0;
        padding: 10px; border: 1px solid var(--border); border-radius: 8px; background: #fff; color: var(--ink);
        font-size: 0.92rem; font-weight: 600; cursor: pointer; }
    .signup-type input { width: auto; margin: 0; }
    .signup-type label:has(input:checked) { border-color: var(--primary); background: var(--card-bg); box-shadow: 0 0 0 3px rgba(139,79,42,0.12); }

    .wizard-nav { display: flex; justify-content: space-between; align-items: center; margin-top: 18px; }
    .wizard-arrow { display: inline-flex; align-items: center; justify-content: center; width: 46px; height: 46px;
        padding: 0; border-radius: 50%; font-size: 1.35rem; }
    .wizard-arrow.secondary { color: var(--ink); }
    .wizard-submit { min-width: 140px; }
    .wizard-nav .spacer { width: 46px; }

    .consent-text { font-size: 0.82rem; max-height: 150px; overflow-y: auto; background: #fff; border: 1px solid var(--border);
        border-radius: 6px; padding: 8px 12px; margin: 8px 0 4px; }
    .consent-text p { margin: 4px 0; }
    .consent-warn { max-height: none; border-color: var(--gold); }
    .consent-check { display: flex; align-items: flex-start; gap: 8px; width: auto; font-size: 0.88rem; font-weight: 600;
        color: var(--ink); margin: 8px 0 14px; cursor: pointer; }
    .consent-check input { width: auto; margin: 3px 0 0; }
</style>

<div class="signup-box">
    <h1>회원가입</h1>

    <c:if test="${not empty errorMessage}">
        <p class="error-message"><c:out value="${errorMessage}" /></p>
    </c:if>

    <div class="card">
        <div class="wizard-steps" aria-hidden="true">
            <span class="ws on" data-for="0"><span class="dot">1</span> 기본 정보</span><span class="bar"></span>
            <span class="ws" data-for="1"><span class="dot">2</span> 추가 정보</span><span class="bar"></span>
            <span class="ws" data-for="2"><span class="dot">3</span> 동의</span>
        </div>

        <form action="${pageContext.request.contextPath}/register" method="post" id="signupForm" novalidate>
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <div class="wizard-viewport" id="wizardViewport">
                <div class="wizard-track" id="wizardTrack">

                    <%-- ① 기본 정보 --%>
                    <section class="wizard-step" aria-label="1단계 기본 정보">
                        <h2>기본 정보</h2>
                        <p class="muted">가입 유형과 로그인에 쓸 정보를 입력해주세요.</p>
                        <div class="signup-type">
                            <label><input type="radio" name="userType" value="APPLICANT" ${param.userType == 'INTERVIEWER' ? '' : 'checked'}> 지원자</label>
                            <label><input type="radio" name="userType" value="INTERVIEWER" ${param.userType == 'INTERVIEWER' ? 'checked' : ''}> 면접관</label>
                        </div>
                        <p><label>아이디</label><input type="text" name="loginId" maxlength="50" required autocomplete="username" value="<c:out value='${param.loginId}' />"></p>
                        <p><label>비밀번호</label><input type="password" name="password" id="password" minlength="8" maxlength="100" required autocomplete="new-password" placeholder="8자 이상"></p>
                        <p><label>비밀번호 확인</label><input type="password" name="passwordConfirm" id="passwordConfirm" required autocomplete="new-password" placeholder="한 번 더 입력"></p>
                        <p><label>이름</label><input type="text" name="name" maxlength="50" required value="<c:out value='${param.name}' />"></p>
                        <p><label>이메일 (선택)</label><input type="email" name="email" value="<c:out value='${param.email}' />"></p>
                        <div class="wizard-nav">
                            <span class="spacer"></span>
                            <button type="button" class="wizard-arrow" data-go="1" aria-label="다음"><span class="ic ic-chevron-right" aria-hidden="true"></span></button>
                        </div>
                    </section>

                    <%-- ② 추가 정보 — 지원자는 나이·구분·학년·전공·관심 분야, 면접관은 회사명 --%>
                    <section class="wizard-step" aria-label="2단계 추가 정보">
                        <h2>추가 정보</h2>
                        <div id="applicant-fields">
                            <p class="muted">맞춤 로드맵과 또래 비교에 쓰입니다.</p>
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
                            <p><label>전공</label><input type="text" name="major" placeholder="예) 컴퓨터공학과" value="<c:out value='${param.major}' />"></p>
                            <p><label>관심 분야</label><input type="text" name="interestField" placeholder="예) 백엔드, 데이터" value="<c:out value='${param.interestField}' />"></p>
                        </div>
                        <div id="interviewer-fields">
                            <p class="muted">면접관 계정은 지원자가 보내 준 공유 링크의 이력을 담아 두고 나란히 비교할 수 있습니다. 지원자를 검색하거나 직접 조회할 수는 없습니다.</p>
                            <p><label>회사명 (선택)</label><input type="text" name="companyName" maxlength="100" value="<c:out value='${param.companyName}' />"></p>
                        </div>
                        <div class="wizard-nav">
                            <button type="button" class="wizard-arrow secondary" data-go="0" aria-label="이전"><span class="ic ic-chevron-left" aria-hidden="true"></span></button>
                            <button type="button" class="wizard-arrow" data-go="2" aria-label="다음"><span class="ic ic-chevron-right" aria-hidden="true"></span></button>
                        </div>
                    </section>

                    <%-- ③ 동의 --%>
                    <section class="wizard-step" aria-label="3단계 동의">
                        <h2>동의</h2>
                        <p class="muted">아래 내용을 확인하고 체크하면 가입할 수 있습니다.</p>
                        <div class="consent-text">
                            <p><strong>수집 항목</strong> 아이디, 비밀번호(암호화 저장), 이름, 이메일(선택), 나이·구분·학년·전공·관심 분야(지원자), 이후 직접 입력하는 스펙·프로젝트·서류</p>
                            <p><strong>이용 목적</strong> 직무 격차 분석, 로드맵·점수 제공, 본인이 만든 공유 링크를 통한 면접관 열람</p>
                            <p><strong>보유 기간</strong> 회원 탈퇴 시까지(탈퇴 후 30일 유예 뒤 파기). 입력·업로드는 모두 본인의 선택이며, 입력하지 않아도 서비스를 쓸 수 있는 항목이 많습니다.</p>
                        </div>
                        <label class="consent-check"><input type="checkbox" name="privacyConsent" value="Y" required> [필수] 개인정보 수집·이용에 동의합니다.</label>
                        <div id="interviewer-notice">
                            <div class="consent-text consent-warn">
                                <p><strong>면접관에게 보이는 자료이니 꼭 확인하세요</strong></p>
                                <p>공유 링크를 만들면 이력서·자소서·프로젝트·서류 등 <strong>내가 공개로 고른 항목은 링크를 받은 면접관에게 그대로 보입니다.</strong>
                                    공개 범위는 링크마다 따로 정하고 언제든 끌 수 있습니다(기본은 비공개). 공개 전에 연락처·주민번호 같은 민감한 정보가 파일에 들어 있지 않은지 직접 확인해 주세요.</p>
                            </div>
                            <label class="consent-check"><input type="checkbox" name="visibilityNotice" value="Y" required> [필수] 위 내용을 숙지했습니다.</label>
                        </div>
                        <div class="wizard-nav">
                            <button type="button" class="wizard-arrow secondary" data-go="1" aria-label="이전"><span class="ic ic-chevron-left" aria-hidden="true"></span></button>
                            <button type="submit" class="wizard-submit">가입하기</button>
                        </div>
                    </section>
                </div>
            </div>
        </form>
    </div>

    <p><a href="${pageContext.request.contextPath}/login">이미 계정이 있으신가요? 로그인</a></p>
</div>

<script>
    (function () {
        var form = document.getElementById('signupForm');
        var track = document.getElementById('wizardTrack');
        var viewport = document.getElementById('wizardViewport');
        var steps = Array.prototype.slice.call(track.querySelectorAll('.wizard-step'));
        var markers = Array.prototype.slice.call(document.querySelectorAll('.wizard-steps .ws'));
        var careerStatus = document.getElementById('careerStatus');
        var password = document.getElementById('password');
        var passwordConfirm = document.getElementById('passwordConfirm');
        var current = 0;

        // 가입 유형과 구분에 맞는 입력칸만 보여주고, 숨긴 칸은 검사·제출에서도 뺀다
        function setShown(box, shown) {
            box.style.display = shown ? '' : 'none';
            box.querySelectorAll('input, select').forEach(function (input) { input.disabled = !shown; });
        }
        function toggleFields() {
            var interviewer = document.querySelector('input[name="userType"]:checked').value === 'INTERVIEWER';
            setShown(document.getElementById('applicant-fields'), !interviewer);
            setShown(document.getElementById('interviewer-fields'), interviewer);
            setShown(document.getElementById('grade-field'), !interviewer && careerStatus.value === 'STUDENT');
            // 면접관에게 보이는 자료 안내는 자료를 올리는 지원자에게만 해당한다
            setShown(document.getElementById('interviewer-notice'), !interviewer);
            fitHeight();
        }

        function fitHeight() { viewport.style.height = steps[current].offsetHeight + 'px'; }

        function show(index) {
            current = index;
            track.style.transform = 'translateX(-' + (100 / steps.length * index) + '%)';
            steps.forEach(function (step, i) { step.inert = i !== index; });
            markers.forEach(function (m, i) {
                m.classList.toggle('on', i === index);
                m.classList.toggle('done', i < index);
            });
            fitHeight();
            var first = steps[index].querySelector('input:not([type=radio]):not([disabled]), select:not([disabled])');
            if (first) { setTimeout(function () { first.focus({ preventScroll: true }); }, 380); }
        }

        // 한 단계 안에서 처음으로 틀린 칸(없으면 null) — 비밀번호 확인은 직접 맞춰 본다
        function firstInvalid(index) {
            passwordConfirm.setCustomValidity(passwordConfirm.value && passwordConfirm.value !== password.value
                ? '비밀번호가 서로 다릅니다.' : '');
            var fields = steps[index].querySelectorAll('input:not([disabled]), select:not([disabled])');
            for (var i = 0; i < fields.length; i++) {
                if (!fields[i].checkValidity()) { return fields[i]; }
            }
            return null;
        }

        document.querySelectorAll('[data-go]').forEach(function (button) {
            button.addEventListener('click', function () {
                var target = Number(button.getAttribute('data-go'));
                if (target > current) {
                    var bad = firstInvalid(current);
                    if (bad) { bad.reportValidity(); return; }
                }
                show(target);
            });
        });

        // 가입하기 — 앞 단계 칸도 다시 확인하고, 틀린 칸이 있으면 그 단계로 돌아가 안내한다
        form.addEventListener('submit', function (event) {
            for (var i = 0; i < steps.length; i++) {
                var bad = firstInvalid(i);
                if (bad) {
                    event.preventDefault();
                    if (i !== current) { show(i); }
                    setTimeout(function () { bad.reportValidity(); }, i !== current ? 400 : 0);
                    return;
                }
            }
        });

        password.addEventListener('input', function () { passwordConfirm.setCustomValidity(''); });
        passwordConfirm.addEventListener('input', function () { passwordConfirm.setCustomValidity(''); });
        document.querySelectorAll('input[name="userType"]').forEach(function (r) { r.addEventListener('change', toggleFields); });
        careerStatus.addEventListener('change', toggleFields);
        window.addEventListener('resize', fitHeight);

        toggleFields();
        show(0);
    })();
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
