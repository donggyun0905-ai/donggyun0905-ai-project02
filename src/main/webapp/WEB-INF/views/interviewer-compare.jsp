<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 비교 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:set var="session" value="${compareView.session}" />
<c:set var="criteria" value="${compareView.criteria}" />
<c:set var="candidates" value="${compareView.candidates}" />
<c:set var="totalWeight" value="0" />
<c:forEach var="c" items="${criteria}"><c:set var="totalWeight" value="${totalWeight + c.weight}" /></c:forEach>

<div class="spread" style="margin-bottom:16px;">
    <h1 style="margin:0;">지원자 비교</h1>
    <span class="pill">평가 세션 · ${empty session.companyName ? '이름 없음' : session.companyName}</span>
</div>
<p class="muted">받은 공유 링크를 담아 지원자를 나란히 비교합니다. 로그인 없이 이 브라우저(쿠키)에만 저장되며, 다른 기기·브라우저에서는 새 목록으로 시작합니다.</p>

<c:if test="${not empty errorMessage}">
    <p class="error-message">${errorMessage}</p>
</c:if>

<div class="row" style="align-items:stretch; margin-top:16px;">
    <div class="card" style="flex:1;">
        <h2>평가 세션 이름</h2>
        <form action="${pageContext.request.contextPath}/share/compare" method="post" style="margin-top:10px;">
            <input type="hidden" name="action" value="renameSession">
            <div class="row">
                <input type="text" name="companyName" placeholder="예) A사 백엔드 채용" value="${session.companyName}" style="flex:1;">
                <button type="submit" class="secondary">저장</button>
            </div>
        </form>

        <h2 style="margin-top:18px;">지원자 담기</h2>
        <form action="${pageContext.request.contextPath}/share/compare" method="post" style="margin-top:10px;">
            <input type="hidden" name="action" value="addCandidate">
            <div class="row">
                <input type="text" name="linkInput" placeholder="공유 링크 주소 또는 토큰을 붙여넣으세요" style="flex:1;">
                <button type="submit">담기</button>
            </div>
        </form>
        <p class="muted" style="margin-top:8px;">지금 ${empty candidates ? 0 : candidates.size()}명을 담았습니다. 지원자가 공유를 멈추거나 링크가 만료되면 비교표에서 빠집니다.</p>
    </div>
    <div class="card" style="flex:1;">
        <h2>회사 요구 역량</h2>
        <p class="muted" style="margin-top:4px;">가중치가 클수록 적합도 점수에 크게 반영됩니다</p>
        <c:choose>
            <c:when test="${empty criteria}">
                <p class="muted" style="margin-top:10px;">아직 등록한 요구 역량이 없습니다.</p>
            </c:when>
            <c:otherwise>
                <div style="display:flex; flex-direction:column; gap:8px; margin-top:10px;">
                    <c:forEach var="c" items="${criteria}">
                        <div class="row spread">
                            <strong>${c.skillName}</strong>
                            <span class="row">
                                <span>가중치 ${c.weight}</span>
                                <form action="${pageContext.request.contextPath}/share/compare" method="post" class="inline-form">
                                    <input type="hidden" name="action" value="removeCriterion">
                                    <input type="hidden" name="criteriaId" value="${c.id}">
                                    <button type="submit" class="link-button">✕</button>
                                </form>
                            </span>
                        </div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
        <form action="${pageContext.request.contextPath}/share/compare" method="post" style="margin-top:12px;">
            <input type="hidden" name="action" value="addCriterion">
            <div class="row">
                <input type="text" name="skillName" placeholder="기술명 (예: Java)" style="flex:2;">
                <input type="number" name="weight" placeholder="가중치" min="1" value="1" style="width:90px;">
                <button type="submit" class="secondary">역량 추가</button>
            </div>
        </form>
    </div>
</div>

<div class="card">
    <h2>나란히 보기</h2>
    <c:choose>
        <c:when test="${empty candidates}">
            <p class="muted" style="margin-top:10px;">아직 담은 지원자가 없습니다.</p>
        </c:when>
        <c:otherwise>
            <table style="margin-top:10px;">
                <tr>
                    <th>항목</th>
                    <c:forEach var="cand" items="${candidates}" varStatus="st">
                        <th>지원자 ${st.index + 1}
                            <form action="${pageContext.request.contextPath}/share/compare" method="post" class="inline-form" style="margin-left:6px;">
                                <input type="hidden" name="action" value="removeCandidate">
                                <input type="hidden" name="itemId" value="${cand.itemId}">
                                <button type="submit" class="link-button">빼기</button>
                            </form>
                        </th>
                    </c:forEach>
                </tr>
                <tr>
                    <td>전공·학년</td>
                    <c:forEach var="cand" items="${candidates}">
                        <td>${empty cand.major ? '미입력' : cand.major} ${cand.grade}</td>
                    </c:forEach>
                </tr>
                <tr>
                    <td>자격증</td>
                    <c:forEach var="cand" items="${candidates}"><td>${cand.certCount}개</td></c:forEach>
                </tr>
                <tr>
                    <td>프로젝트</td>
                    <c:forEach var="cand" items="${candidates}"><td>${cand.projectCount}개</td></c:forEach>
                </tr>
                <c:forEach var="c" items="${criteria}" varStatus="cst">
                    <tr>
                        <td>${c.skillName} · 가중치 ${c.weight}</td>
                        <c:forEach var="cand" items="${candidates}">
                            <c:set var="has" value="${cand.hasSkillByCriterion[cst.index]}" />
                            <td>
                                <c:choose>
                                    <c:when test="${empty has}"><span class="muted">비공개</span></c:when>
                                    <c:when test="${has}"><span style="color:var(--teal);">갖춤</span></c:when>
                                    <c:otherwise><span style="color:var(--danger);">없음</span></c:otherwise>
                                </c:choose>
                            </td>
                        </c:forEach>
                    </tr>
                </c:forEach>
                <tr style="background:#f1e4c8;">
                    <td><strong>적합도 점수</strong></td>
                    <c:forEach var="cand" items="${candidates}">
                        <td><strong><c:choose><c:when test="${empty cand.fitScorePercent}">비공개</c:when><c:otherwise>${cand.fitScorePercent}점</c:otherwise></c:choose></strong></td>
                    </c:forEach>
                </tr>
                <tr>
                    <td>성장 잠재력</td>
                    <c:forEach var="cand" items="${candidates}">
                        <td>
                            <c:choose>
                                <c:when test="${empty cand.growth}"><span class="muted">지원자가 공개하지 않음</span></c:when>
                                <c:otherwise>${cand.growth.fromScore} → ${cand.growth.toScore}</c:otherwise>
                            </c:choose>
                        </td>
                    </c:forEach>
                </tr>
            </table>
            <p class="muted" style="font-size:0.78rem; margin-top:10px; margin-bottom:0;">적합도 = 맞춘 역량의 가중치 합 ÷ 전체 가중치 합(${totalWeight}) × 100. 지원자가 기술 스택을 공개하지 않으면 계산하지 않습니다. 이 비교는 지원자들이 공개한 항목만 사용합니다.</p>
        </c:otherwise>
    </c:choose>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
