<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 프로젝트 제출 폼의 입력칸(개발일지 4-4) — PROJECT 단계와 CORE/ADVANCED 단계가 같이 쓴다.
     호출하는 쪽이 request 속성 draft(이전에 낸 프로젝트·서류, 없으면 비어 있음)를 담아서 include한다.
     폼을 다시 그릴 때(검증 실패)는 방금 입력한 값(param)을 우선해서 보여준다.

     profileMode(request 속성, 기본 false): 프로필에서 프로젝트를 직접 등록·수정할 때 true.
     그때는 README·실행 화면 캡처를 필수로 받지 않는다 — 예전에 한 프로젝트를 적어 두는 자리라
     서류가 없다고 등록을 막으면 아무것도 못 적는다(2026-10-07 사용자 요청). 로드맵 PROJECT 단계는
     이 서류가 "단계를 끝냈다"는 증빙이라 그대로 필수다. --%>
<c:set var="pTitle" value="${not empty param.title ? param.title : draft.project.title}" />
<c:set var="pDesc" value="${not empty param.description ? param.description : draft.project.description}" />
<c:set var="pTech" value="${not empty param.techStack ? param.techStack : draft.project.techStack}" />
<c:set var="pStart" value="${not empty param.startDate ? param.startDate : draft.project.startDate}" />
<c:set var="pEnd" value="${not empty param.endDate ? param.endDate : draft.project.endDate}" />
<c:set var="pRepo" value="${not empty param.repoUrl ? param.repoUrl : draft.project.repoUrl}" />
<c:set var="pDeploy" value="${not empty param.deployUrl ? param.deployUrl : draft.project.deployUrl}" />
<c:set var="pRetro" value="${not empty param.retrospective ? param.retrospective : draft.project.retrospective}" />
<c:if test="${not empty draft}">
    <p class="muted" style="font-size:0.82rem;">이전에 제출한 내용이 채워져 있어요. 이미 낸 서류는 다시 올리지 않아도 됩니다.</p>
</c:if>
<p><label>프로젝트명</label><input type="text" name="title" value="<c:out value='${pTitle}' />" required></p>
<p><label>설명 (무엇을 했는지)</label><textarea name="description" required><c:out value="${pDesc}" /></textarea></p>
<p><label>사용 기술</label><input type="text" name="techStack" value="<c:out value='${pTech}' />" placeholder="예: Java, Spring, MySQL" required></p>
<p class="row">
    <span style="flex:1;"><label>시작일</label><input type="date" name="startDate" value="<c:out value='${pStart}' />"></span>
    <span style="flex:1;"><label>종료일</label><input type="date" name="endDate" value="<c:out value='${pEnd}' />"></span>
</p>
<p class="row">
    <span style="flex:1;"><label>코드 저장소 링크 (선택)</label><input type="url" name="repoUrl" value="<c:out value='${pRepo}' />" placeholder="https://github.com/..."></span>
    <span style="flex:1;"><label>배포 주소 (선택)</label><input type="url" name="deployUrl" value="<c:out value='${pDeploy}' />" placeholder="https://..."></span>
</p>
<c:set var="linkRows" value="${draft.links}" />
<%@ include file="/WEB-INF/views/common/project-link-fields.jspf" %>
<p><label>완료 회고 (선택, 1000자 이내)</label><textarea name="retrospective" maxlength="1000" placeholder="무엇을 배웠고 어디가 아쉬웠는지"><c:out value="${pRetro}" /></textarea></p>

<fieldset style="border:1px solid var(--border, #ddd); border-radius:6px; padding:10px 12px; margin:10px 0;">
    <legend style="font-size:0.9rem;">
        <c:choose>
            <c:when test="${profileMode}">문서 체크리스트 (선택) — 가지고 있는 것만 올리면 됩니다</c:when>
            <c:otherwise>문서 체크리스트 — README와 실행 화면 캡처는 꼭 제출해야 합니다</c:otherwise>
        </c:choose>
    </legend>
    <c:forEach var="type" items="${docTypeLabels}">
        <c:set var="required" value="${not profileMode and requiredDocTypes.contains(type.key)}" />
        <c:set var="old" value="${draft.docs[type.key]}" />
        <p style="margin:6px 0;">
            <label><c:out value="${type.value}" /><c:if test="${required}"> <span style="color:var(--danger);">(필수)</span></c:if></label>
            <c:if test="${not empty old and old.submitted}">
                <span class="muted" style="font-size:0.82rem;"><span class="ic ic-check" aria-hidden="true"></span> 제출됨: <c:out value="${old.documentName}" /> — 새 파일을 올리면 이 파일도 보관함에 남고 새 파일이 체크리스트에 올라갑니다</span>
            </c:if>
            <input type="file" name="doc_${type.key}" ${required and not (not empty old and old.submitted) ? 'required' : ''}>
            <c:if test="${not required}">
                <label style="font-weight:normal; font-size:0.85rem;"><input type="checkbox" name="na_${type.key}" ${not empty old and old.notApplicable ? 'checked' : ''}> 해당 없음</label>
            </c:if>
        </p>
    </c:forEach>
</fieldset>

<fieldset style="border:1px solid var(--border, #ddd); border-radius:6px; padding:10px 12px; margin:10px 0;">
    <legend style="font-size:0.9rem;">기술 활용 설명서 (선택) — 쓴 기술을 어떻게 활용했는지 한두 문장으로</legend>
    <c:forEach var="i" begin="0" end="4">
        <c:set var="nameKey" value="techName_${i}" />
        <c:set var="descKey" value="techDesc_${i}" />
        <p class="row" style="margin:6px 0; align-items:flex-start;">
            <span style="flex:1;"><input type="text" name="techName_${i}" placeholder="기술 이름 (예: MySQL)" value="<c:out value='${param[nameKey]}' />"></span>
            <span style="flex:3;"><input type="text" name="techDesc_${i}" maxlength="1000" placeholder="어떻게 활용했는지" value="<c:out value='${param[descKey]}' />"></span>
            <label style="font-weight:normal; font-size:0.8rem; white-space:nowrap;"><input type="checkbox" name="techConsent_${i}"> 학습 활용 동의</label>
        </p>
    </c:forEach>
    <c:if test="${not empty draft.notes}">
        <p class="muted" style="font-size:0.8rem; margin:4px 0 0;">저장된 설명:
            <c:forEach var="n" items="${draft.notes}" varStatus="st"><b><c:out value="${n.skillName}" /></b> <c:out value="${n.description}" /><c:if test="${!st.last}"> / </c:if></c:forEach>
        </p>
    </c:if>
</fieldset>

<p><label>그 밖의 증빙 파일 (선택, 여러 개 가능)</label><input type="file" name="files" multiple></p>
