<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="관리자 · 회원 관리 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<c:set var="adminTab" value="users" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1><span class="ic ic-wrench" aria-hidden="true"></span> 관리자</h1>
<%@ include file="/WEB-INF/views/common/admin-tabs.jspf" %>

<c:if test="${not empty adminMessage}">
    <div class="banner"><span><c:out value="${adminMessage}" /></span></div>
</c:if>
<c:if test="${not empty adminError}">
    <div class="banner" style="background:var(--danger-bg); color:var(--danger);"><span><c:out value="${adminError}" /></span></div>
</c:if>

<div class="card">
    <h2>회원 검색</h2>
    <form method="get" action="${pageContext.request.contextPath}/admin/users" class="row" style="margin-top:10px;">
        <input type="text" name="q" value="${keyword}" placeholder="아이디·이름·이메일로 검색" style="flex:1;">
        <button type="submit">검색</button>
    </form>
    <c:if test="${not empty keyword}">
        <c:choose>
            <c:when test="${empty results}">
                <p class="muted" style="margin-top:10px;">검색 결과가 없습니다.</p>
            </c:when>
            <c:otherwise>
                <table style="margin-top:10px; width:100%;">
                    <tr><th style="text-align:left;">아이디</th><th>이름</th><th>이메일</th><th>유형</th><th>상태</th><th></th></tr>
                    <c:forEach var="u" items="${results}">
                        <tr>
                            <td><c:out value="${u.loginId}" /></td>
                            <td><c:out value="${u.name}" default="—" /></td>
                            <td class="muted"><c:out value="${u.email}" default="—" /></td>
                            <td><c:out value="${u.userType}" /></td>
                            <td>
                                <c:choose>
                                    <c:when test="${u.deleted}"><span class="chip" style="background:var(--danger-bg); color:var(--danger);">탈퇴</span></c:when>
                                    <c:when test="${not empty u.withdrawRequestedAt}"><span class="chip chip-gold">탈퇴 유예중</span></c:when>
                                    <c:otherwise><span class="chip chip-teal">정상</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td><a href="${pageContext.request.contextPath}/admin/users?q=${keyword}&edit=${u.id}">관리</a></td>
                        </tr>
                    </c:forEach>
                </table>
            </c:otherwise>
        </c:choose>
    </c:if>
</div>

<c:if test="${not empty editUser}">
    <div class="card">
        <h2><c:out value="${editUser.loginId}" /> 관리</h2>
        <form method="post" action="${pageContext.request.contextPath}/admin/users" style="margin-top:10px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="updateProfile">
            <input type="hidden" name="userId" value="${editUser.id}">
            <input type="hidden" name="returnQuery" value="q=${keyword}&edit=${editUser.id}">
            <div class="row" style="gap:12px; flex-wrap:wrap;">
                <p style="flex:1; min-width:160px;"><label>이름</label><input type="text" name="name" value="<c:out value='${editUser.name}' />"></p>
                <p style="flex:1; min-width:120px;"><label>나이</label><input type="number" name="age" value="${editUser.age}" min="0" max="120"></p>
                <p style="flex:1; min-width:160px;"><label>이메일</label><input type="email" name="email" value="<c:out value='${editUser.email}' />"></p>
            </div>
            <div class="row" style="gap:12px; flex-wrap:wrap;">
                <p style="flex:1; min-width:160px;"><label>전공</label><input type="text" name="major" value="<c:out value='${editUser.major}' />"></p>
                <p style="flex:1; min-width:120px;"><label>학년</label><input type="text" name="grade" value="<c:out value='${editUser.grade}' />"></p>
                <p style="flex:1; min-width:160px;">
                    <label>구분</label>
                    <select name="careerStatus">
                        <option value="">선택 안 함</option>
                        <option value="STUDENT" ${editUser.careerStatus == 'STUDENT' ? 'selected' : ''}>학생</option>
                        <option value="JOB_SEEKER" ${editUser.careerStatus == 'JOB_SEEKER' ? 'selected' : ''}>취준생</option>
                        <option value="EMPLOYED" ${editUser.careerStatus == 'EMPLOYED' ? 'selected' : ''}>직장인</option>
                    </select>
                </p>
            </div>
            <div class="row" style="gap:12px; flex-wrap:wrap;">
                <p style="flex:1; min-width:160px;"><label>관심 분야</label><input type="text" name="interestField" value="<c:out value='${editUser.interestField}' />"></p>
                <p style="flex:2; min-width:200px;">
                    <label>희망 직무</label>
                    <select name="desiredJobId">
                        <option value="">선택 안 함</option>
                        <c:forEach var="job" items="${jobs}">
                            <option value="${job.id}" ${editUser.desiredJobId == job.id ? 'selected' : ''}><c:out value="${job.jobName}" /></option>
                        </c:forEach>
                    </select>
                </p>
                <p style="flex:1; min-width:140px;">
                    <label>희망 직무 상태</label>
                    <select name="desiredJobStatus">
                        <option value="UNSET" ${editUser.desiredJobStatus == 'UNSET' ? 'selected' : ''}>미설정</option>
                        <option value="SET" ${editUser.desiredJobStatus == 'SET' ? 'selected' : ''}>설정됨</option>
                    </select>
                </p>
            </div>
            <button type="submit">프로필 저장</button>
        </form>

        <hr style="margin:18px 0; border-color:var(--border);">

        <div class="row" style="gap:24px; flex-wrap:wrap; align-items:flex-start;">
            <form method="post" action="${pageContext.request.contextPath}/admin/users"
                  onsubmit="return confirm('새 비밀번호로 바꿀까요? 당사자에게 직접 전달해야 합니다.');">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="resetPassword">
                <input type="hidden" name="userId" value="${editUser.id}">
                <input type="hidden" name="returnQuery" value="q=${keyword}&edit=${editUser.id}">
                <p><label>비밀번호 재설정 (8자 이상)</label></p>
                <div class="row">
                    <input type="text" name="newPassword" placeholder="새 비밀번호" required minlength="8">
                    <button type="submit" class="secondary">재설정</button>
                </div>
            </form>

            <div>
                <p><label>계정 상태</label></p>
                <c:choose>
                    <c:when test="${editUser.deleted}">
                        <p class="muted">이미 탈퇴 처리된 계정입니다.</p>
                    </c:when>
                    <c:when test="${not empty editUser.withdrawRequestedAt}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/users">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="action" value="cancelWithdrawal">
                            <input type="hidden" name="userId" value="${editUser.id}">
                            <input type="hidden" name="returnQuery" value="q=${keyword}&edit=${editUser.id}">
                            <button type="submit" class="secondary">탈퇴 유예 취소(복구)</button>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <form method="post" action="${pageContext.request.contextPath}/admin/users"
                              onsubmit="return confirm('이 계정을 탈퇴 처리할까요?');">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="action" value="softDelete">
                            <input type="hidden" name="userId" value="${editUser.id}">
                            <input type="hidden" name="returnQuery" value="q=${keyword}&edit=${editUser.id}">
                            <button type="submit" style="background:var(--danger); border-color:var(--danger);">탈퇴 처리</button>
                        </form>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
        <p class="muted" style="margin-top:12px; margin-bottom:0;">로그인 아이디와 관리자 권한(user_type)은 여기서 바꿀 수 없습니다.</p>
    </div>
</c:if>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
