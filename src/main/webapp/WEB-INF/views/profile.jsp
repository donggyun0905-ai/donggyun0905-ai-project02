<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>내 프로필 - 스펙 오디세이</title>
</head>
<body>
<h1>내 프로필</h1>
<p><a href="${pageContext.request.contextPath}/logout">로그아웃</a></p>

<h2>기본 정보</h2>
<form action="${pageContext.request.contextPath}/profile" method="post">
    <p><label>이메일 <input type="email" name="email" value="${user.email}"></label></p>
    <p><label>전공 <input type="text" name="major" value="${user.major}"></label></p>
    <p><label>학년 <input type="text" name="grade" value="${user.grade}"></label></p>
    <p><label>관심 분야 <input type="text" name="interestField" value="${user.interestField}"></label></p>
    <p>
        희망 직무:
        <label><input type="radio" name="desiredJobStatus" value="UNSET"
                       ${user.desiredJobStatus == 'UNSET' ? 'checked' : ''}> 아직 모르겠음</label>
        <label><input type="radio" name="desiredJobStatus" value="SET"
                       ${user.desiredJobStatus == 'SET' ? 'checked' : ''}> 선택함</label>
        <select name="desiredJobId">
            <option value="">-- 직무 선택 --</option>
            <c:forEach var="job" items="${jobs}">
                <option value="${job.id}" ${job.id == user.desiredJobId ? 'selected' : ''}>${job.jobName}</option>
            </c:forEach>
        </select>
    </p>
    <button type="submit">기본정보 저장</button>
</form>

<h2>보유 스펙</h2>
<ul>
    <c:forEach var="spec" items="${specs}">
        <li>
            [${spec.specType}] ${spec.title}
            <c:if test="${not empty spec.issuer}"> · ${spec.issuer}</c:if>
            <c:if test="${not empty spec.score}"> · ${spec.score}</c:if>
            <c:if test="${not empty spec.acquiredDate}"> · ${spec.acquiredDate}</c:if>
            <form action="${pageContext.request.contextPath}/profile/specs" method="post" style="display:inline;">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="specId" value="${spec.id}">
                <button type="submit">삭제</button>
            </form>
        </li>
    </c:forEach>
</ul>
<form action="${pageContext.request.contextPath}/profile/specs" method="post">
    <input type="hidden" name="action" value="add">
    <select name="specType">
        <option value="CERT">자격증</option>
        <option value="LANGUAGE">어학</option>
        <option value="AWARD">수상</option>
    </select>
    <input type="text" name="title" placeholder="명칭" required>
    <input type="text" name="issuer" placeholder="발급기관">
    <input type="text" name="score" placeholder="점수(어학 등)">
    <input type="date" name="acquiredDate">
    <button type="submit">스펙 추가</button>
</form>

<h2>프로젝트 · 경험</h2>
<ul>
    <c:forEach var="project" items="${projects}">
        <li>
            <strong>${project.title}</strong>
            (${project.startDate} ~ ${project.endDate})<br>
            ${project.description}<br>
            <c:if test="${not empty project.techStack}">기술스택: ${project.techStack}</c:if>
            <form action="${pageContext.request.contextPath}/profile/projects" method="post" style="display:inline;">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="projectId" value="${project.id}">
                <button type="submit">삭제</button>
            </form>
        </li>
    </c:forEach>
</ul>
<form action="${pageContext.request.contextPath}/profile/projects" method="post">
    <input type="hidden" name="action" value="add">
    <p><input type="text" name="title" placeholder="프로젝트명" required></p>
    <p><textarea name="description" placeholder="설명"></textarea></p>
    <p><input type="text" name="techStack" placeholder="사용 기술 (예: Java, Spring, MySQL)"></p>
    <p>시작일 <input type="date" name="startDate"> 종료일 <input type="date" name="endDate"></p>
    <button type="submit">프로젝트 추가</button>
</form>

<h2>보유 기술 스택</h2>
<ul>
    <c:forEach var="skill" items="${skills}">
        <li>
            ${skill.rawInput}
            <c:if test="${not empty skill.proficiency}"> (${skill.proficiency})</c:if>
            <form action="${pageContext.request.contextPath}/profile/skills" method="post" style="display:inline;">
                <input type="hidden" name="action" value="delete">
                <input type="hidden" name="userSkillId" value="${skill.id}">
                <button type="submit">삭제</button>
            </form>
        </li>
    </c:forEach>
</ul>
<form action="${pageContext.request.contextPath}/profile/skills" method="post">
    <input type="hidden" name="action" value="add">
    <input type="text" name="rawInput" placeholder="기술명 (예: Python, React)" required>
    <select name="proficiency">
        <option value="">숙련도 선택 안함</option>
        <option value="BEGINNER">입문</option>
        <option value="INTERMEDIATE">중급</option>
        <option value="ADVANCED">고급</option>
    </select>
    <button type="submit">기술 추가</button>
</form>

</body>
</html>
