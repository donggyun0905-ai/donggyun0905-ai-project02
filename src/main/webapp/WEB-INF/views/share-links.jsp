<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="공유 링크 관리 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>공유 링크 관리</h1>
<p class="muted">면접관에게 보여줄 이력을 링크로 공유합니다. 링크를 받은 사람만 로그인 없이 읽기 전용으로 볼 수 있고, 언제든 공유를 멈출 수 있습니다.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <c:if test="${not empty createdLink}">
            <div class="card">
                <div class="spread"><h2>링크를 만들었습니다</h2><span class="chip chip-teal">공유 중</span></div>
                <p class="muted" style="margin:6px 0; font-size:0.84rem;">
                    <c:if test="${not empty createdLink.label}"><c:out value="${createdLink.label}" /> · </c:if>
                    <c:choose>
                        <c:when test="${not empty createdExpiresAt}">만료 ${createdExpiresAt}</c:when>
                        <c:otherwise>만료 없음</c:otherwise>
                    </c:choose>
                </p>
                <div class="row">
                    <input type="text" value="<c:out value='${shareBaseUrl}${createdLink.token}' />" readonly style="flex:1;">
                    <button type="button" class="secondary copy-url">주소 복사</button>
                </div>
                <p class="muted" style="font-size:0.78rem; margin:8px 0 0;">이 주소를 아는 사람은 로그인 없이 볼 수 있습니다. 면접관에게만 전달하세요.</p>
            </div>
        </c:if>

        <div class="card">
            <h2>새 링크 만들기</h2>
            <c:if test="${not empty errorMessage}">
                <p class="error-message"><c:out value="${errorMessage}" /></p>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/share-links">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <div class="row" style="margin-top:10px;">
                    <span style="flex:2;"><label for="label">메모 (나만 보임)</label><input type="text" id="label" name="label" maxlength="50" placeholder="예) A사 백엔드 지원" value="<c:out value='${param.label}' />"></span>
                    <span style="flex:1;"><label for="expiryDays">만료</label>
                        <select id="expiryDays" name="expiryDays">
                            <option value="7">7일 뒤</option>
                            <option value="30" selected>30일 뒤</option>
                            <option value="90">90일 뒤</option>
                            <option value="0">만료 없음</option>
                        </select>
                    </span>
                </div>
                <div class="card" style="background:#fff; margin:12px 0 0;">
                    <strong style="font-size:0.88rem;">공개 범위</strong>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeBasic" style="width:auto;" checked> <strong>기본 이력</strong> — 이름, 전공, 자격증, 프로젝트 타임라인</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeSkills" style="width:auto;" checked> <strong>보유 기술 스택</strong> — 면접관의 적합도 계산에 쓰입니다</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeGrowth" style="width:auto;"> <strong>성장 잠재력</strong> — 최근 스펙이 늘어난 속도</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeResume" style="width:auto;" checked> <strong>이력서 파일</strong> — 내 프로필에 올린 이력서를 내려받을 수 있게 합니다</label></p>
                    <p style="margin:8px 0;"><label style="display:inline; width:auto;"><input type="checkbox" name="scopeCoverLetter" style="width:auto;" checked> <strong>자소서 파일</strong> — 내 프로필에 올린 자소서를 내려받을 수 있게 합니다</label></p>
                    <p class="muted" style="font-size:0.78rem; margin:6px 0 0;">💡 이력서·자소서를 함께 공개하면 면접관이 링크 하나로 기본 이력부터 서류까지 한눈에 볼 수 있어 편해요. 올려 둔 파일이 없으면 면접관에게는 "아직 올리지 않았습니다"로만 보입니다.</p>
                    <p class="muted" style="font-size:0.78rem; margin:6px 0 0;">격차 분석, 등급과 점수, 미션 기록, 이력서·자소서 외의 서류, AI 활용 기록은 어떤 경우에도 공유되지 않습니다.</p>
                </div>
                <button type="submit" style="margin-top:12px;">링크 만들기</button>
            </form>
        </div>

        <div class="card">
            <h2>내 공유 링크</h2>
            <c:choose>
                <c:when test="${empty links}">
                    <p class="muted" style="margin-top:10px;">아직 만든 링크가 없습니다. 위에서 첫 링크를 만들어 보세요.</p>
                </c:when>
                <c:otherwise>
                    <ul class="item-list" style="margin-top:10px;">
                        <c:forEach var="link" items="${links}">
                            <li>
                                <div class="spread">
                                    <strong><c:out value="${link.label}" default="메모 없음" /></strong>
                                    <c:choose>
                                        <c:when test="${link.status == 'ACTIVE'}"><span class="chip chip-teal">공유 중</span></c:when>
                                        <c:when test="${link.status == 'STOPPED'}"><span class="chip chip-locked">공유 중단됨</span></c:when>
                                        <c:otherwise><span class="chip chip-danger">만료됨</span></c:otherwise>
                                    </c:choose>
                                </div>
                                <p class="muted" style="margin:6px 0; font-size:0.84rem;">
                                    <c:choose>
                                        <c:when test="${link.status == 'EXPIRED'}">${link.expiresDate}에 만료</c:when>
                                        <c:when test="${empty link.expiresDate}">만료 없음</c:when>
                                        <c:otherwise>만료 ${link.expiresDate}</c:otherwise>
                                    </c:choose>
                                    · 공개: ${link.scopeText}
                                    <c:choose>
                                        <c:when test="${link.status == 'STOPPED'}"> · 이 링크로는 지금 아무도 볼 수 없습니다</c:when>
                                        <c:when test="${link.viewCount > 0}"> · 열람 ${link.viewCount}회 (마지막 ${link.lastViewedDate})</c:when>
                                        <c:when test="${link.status == 'ACTIVE'}"> · 아직 열람 없음</c:when>
                                    </c:choose>
                                </p>
                                <c:if test="${link.status == 'ACTIVE'}">
                                    <div class="row">
                                        <input type="text" value="<c:out value='${shareBaseUrl}${link.token}' />" readonly style="flex:1;">
                                        <button type="button" class="secondary copy-url">주소 복사</button>
                                    </div>
                                </c:if>
                                <div class="row" style="margin-top:8px;">
                                    <c:if test="${link.status == 'ACTIVE'}">
                                        <form method="post" action="${pageContext.request.contextPath}/share-links">
                                            <input type="hidden" name="_csrf" value="${csrfToken}">
                                            <input type="hidden" name="action" value="stop">
                                            <input type="hidden" name="linkId" value="${link.id}">
                                            <button type="submit" class="secondary">공유 중단</button>
                                        </form>
                                        <a href="${pageContext.request.contextPath}/share/${link.token}" target="_blank" rel="noopener">면접관 화면 미리보기</a>
                                    </c:if>
                                    <c:if test="${link.status == 'STOPPED'}">
                                        <form method="post" action="${pageContext.request.contextPath}/share-links">
                                            <input type="hidden" name="_csrf" value="${csrfToken}">
                                            <input type="hidden" name="action" value="resume">
                                            <input type="hidden" name="linkId" value="${link.id}">
                                            <button type="submit" class="secondary">다시 공유하기</button>
                                        </form>
                                    </c:if>
                                    <form method="post" action="${pageContext.request.contextPath}/share-links" class="delete-link">
                                        <input type="hidden" name="_csrf" value="${csrfToken}">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="linkId" value="${link.id}">
                                        <button type="submit" class="link-button">삭제</button>
                                    </form>
                                </div>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<script>
    // 주소 복사 — 버튼 바로 앞의 주소 입력칸 값을 클립보드에 넣는다
    document.querySelectorAll('.copy-url').forEach(function (button) {
        button.addEventListener('click', function () {
            var input = button.previousElementSibling;
            input.select();
            // navigator.clipboard는 https나 localhost에서만 있다. IP 주소(http)로 접속하면 없으므로
            // 그때는 선택해 둔 입력칸을 예전 방식(execCommand)으로 복사한다.
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(input.value).then(showCopied, copySelected);
            } else {
                copySelected();
            }

            function copySelected() {
                var copied = false;
                try {
                    copied = document.execCommand('copy');
                } catch (e) {
                    copied = false;
                }
                if (copied) {
                    showCopied();
                } else {
                    button.textContent = 'Ctrl+C로 복사하세요';
                }
            }

            function showCopied() {
                button.textContent = '복사됨';
                setTimeout(function () { button.textContent = '주소 복사'; }, 2000);
            }
        });
    });
    document.querySelectorAll('.delete-link').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            if (!confirm('이 링크를 삭제할까요? 삭제하면 이 주소로는 더 이상 볼 수 없고 되돌릴 수 없습니다.')) {
                event.preventDefault();
            }
        });
    });
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
