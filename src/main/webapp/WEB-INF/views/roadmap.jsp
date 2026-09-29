<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로드맵 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:choose>
    <%-- 희망 직무 미설정 — 로드맵은 목표 직무가 있어야 의미가 있어서 내용 대신 작은 안내 카드만 보여준다.
         희망 직무를 모르면 직무 찾기(설문 추천)로, 이미 알고 있으면 프로필에서 바로 정할 수 있게 둘 다 안내한다. --%>
    <c:when test="${noTargetJob}">
        <div class="card empty-state" style="max-width:460px; margin:40px auto;">
            <div class="icon">🗺️</div>
            <h2>희망 직무를 먼저 정해주세요</h2>
            <p class="muted">로드맵은 목표 직무가 있어야 만들 수 있습니다. 아직 정하지 못했다면 직무 찾기에서 추천을 받아보고, 이미 알고 있다면 프로필에서 바로 선택해주세요.</p>
            <div class="row" style="justify-content:center; gap:10px; margin-top:10px;">
                <a class="btn" href="${pageContext.request.contextPath}/job-discovery">직무 찾기로 가기</a>
                <a class="btn secondary" href="${pageContext.request.contextPath}/profile">프로필로 가기</a>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        <h1>🗺️ 내 로드맵</h1>

        <c:if test="${not empty errorMessage}">
            <p class="error-message">${errorMessage}</p>
        </c:if>

        <div class="two-col" style="margin-top:16px;">
        <div class="primary">
        <c:choose>
            <c:when test="${empty roadmap}">
                <div class="card">
                    <p>아직 생성된 로드맵이 없습니다. 먼저 격차 분석을 완료해야 만들 수 있습니다.</p>
                    <form action="${pageContext.request.contextPath}/roadmap" method="post">
                        <input type="hidden" name="action" value="generate">
                        <button type="submit">로드맵 생성하기</button>
                    </form>
                </div>
            </c:when>
            <c:otherwise>
        <p class="muted">버전 ${roadmap.version} · 목표 수준 ${roadmap.targetLevel}</p>

        <%-- 티어 4개(입문/핵심/심화/전문가)를 한눈에 보여주는 진행도 띠. 비어 있는 티어(그 단계까지
             갈 만큼 부족한 기술이 없었던 경우)는 회색 점으로, 잠긴 티어는 자물쇠로 표시한다. --%>
        <div class="row" style="gap:10px; flex-wrap:wrap; margin:14px 0;">
            <c:forEach var="t" items="${progress.tiers}">
                <c:set var="tierLabel"
                       value="${t.tier == 'ENTRY' ? '입문' : t.tier == 'CORE' ? '핵심' : t.tier == 'ADVANCED' ? '심화' : '전문가'}" />
                <span class="chip ${t.emptyTier ? 'chip-locked' : !t.unlocked ? 'chip-locked' : t.complete ? 'chip-teal' : 'chip-gold'}">
                    <c:choose>
                        <c:when test="${t.emptyTier}">${tierLabel} · 해당 없음</c:when>
                        <c:when test="${!t.unlocked}">🔒 ${tierLabel}</c:when>
                        <c:otherwise>${tierLabel} ${t.done}/${t.total} (${t.percent}%)</c:otherwise>
                    </c:choose>
                </span>
            </c:forEach>
        </div>

        <c:choose>
            <c:when test="${progress.journeyComplete}">
                <div class="banner">🎉 지금까지 분석된 부족 기술을 모두 채웠습니다! 새로 재분석하면 다음 목표가 이어집니다.</div>
            </c:when>
            <c:otherwise>
                <c:set var="currentTier" value="${progress.currentTier}" />
                <c:set var="currentTierLabel"
                       value="${currentTier.tier == 'ENTRY' ? '입문' : currentTier.tier == 'CORE' ? '핵심' : currentTier.tier == 'ADVANCED' ? '심화' : '전문가'}" />
                <h2 style="margin:20px 0 10px;">지금 할 일 (${currentTierLabel})</h2>
                <div class="progress-track"><div class="progress-fill" style="width:${currentTier.percent}%;"></div></div>
                <ul class="item-list">
                    <c:forEach var="step" items="${steps}">
                        <c:if test="${step.tier == currentTier.tier}">
                            <li class="${step.completed ? 'completed' : ''}">
                                <span class="chip chip-gold">${currentTierLabel}</span>
                                <span class="chip chip-teal">
                                    <c:choose>
                                        <c:when test="${step.stepType == 'CERT'}">자격증</c:when>
                                        <c:when test="${step.stepType == 'PROJECT'}">프로젝트</c:when>
                                        <c:otherwise>기술</c:otherwise>
                                    </c:choose>
                                </span>
                                <c:if test="${step.completed}"><span style="color:var(--teal); font-weight:bold;">✔ 완료</span></c:if>
                                <p style="margin:8px 0;">${step.reason}</p>
                                <c:choose>
                                    <c:when test="${step.stepType == 'PROJECT'}">
                                        <c:choose>
                                            <c:when test="${step.completed}">
                                                <a href="${pageContext.request.contextPath}/profile">프로필에서 확인 →</a>
                                            </c:when>
                                            <c:otherwise>
                                                <details>
                                                    <summary>프로젝트 등록하고 완료하기</summary>
                                                    <form action="${pageContext.request.contextPath}/roadmap" method="post"
                                                          enctype="multipart/form-data" style="margin-top:10px;">
                                                        <input type="hidden" name="action" value="completeProject">
                                                        <input type="hidden" name="stepId" value="${step.id}">
                                                        <p><label>프로젝트명</label><input type="text" name="title" required></p>
                                                        <p><label>설명 (무엇을 했는지)</label><textarea name="description" required></textarea></p>
                                                        <p><label>사용 기술</label><input type="text" name="techStack" placeholder="예: Java, Spring, MySQL"></p>
                                                        <p class="row">
                                                            <span style="flex:1;"><label>시작일</label><input type="date" name="startDate"></span>
                                                            <span style="flex:1;"><label>종료일</label><input type="date" name="endDate"></span>
                                                        </p>
                                                        <p><label>증빙 파일(여러 개 가능, 필수)</label><input type="file" name="files" multiple required></p>
                                                        <button type="submit">등록하고 완료하기</button>
                                                    </form>
                                                </details>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:when>
                                    <c:otherwise>
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <c:choose>
                                                <c:when test="${step.completed}">
                                                    <input type="hidden" name="completed" value="false">
                                                    <button type="submit" class="link-button">완료 취소</button>
                                                </c:when>
                                                <c:otherwise>
                                                    <input type="hidden" name="completed" value="true">
                                                    <button type="submit">완료 체크</button>
                                                </c:otherwise>
                                            </c:choose>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </li>
                        </c:if>
                    </c:forEach>
                </ul>
            </c:otherwise>
        </c:choose>

        <c:set var="nextLockedTier" value="${progress.nextLockedTier}" />
        <c:if test="${not empty nextLockedTier}">
            <c:set var="nextTierLabel"
                   value="${nextLockedTier.tier == 'ENTRY' ? '입문' : nextLockedTier.tier == 'CORE' ? '핵심' : nextLockedTier.tier == 'ADVANCED' ? '심화' : '전문가'}" />
            <h2 style="margin:20px 0 4px;">다음 단계 미리보기 (${nextTierLabel})</h2>
            <p class="muted">지금 할 일을 다 끝내면 완료 체크를 할 수 있게 풀립니다.</p>
            <ul class="item-list locked">
                <c:forEach var="step" items="${steps}">
                    <c:if test="${step.tier == nextLockedTier.tier}">
                        <li class="${step.completed ? 'completed' : ''}">
                            <c:choose>
                                <%-- 앞 티어를 다시 미완료로 돌려 이 티어가 다시 잠겨도, 잠기기 전에 이미
                                     완료한 건 그대로 완료로 보여준다 — 되돌렸다고 완료 기록이 지워지는 게 아니라서. --%>
                                <c:when test="${step.completed}"><span style="color:var(--teal); font-weight:bold;">✔ 완료</span></c:when>
                                <c:otherwise><span class="chip chip-locked">🔒 잠김</span></c:otherwise>
                            </c:choose>
                            ${step.reason}
                        </li>
                    </c:if>
                </c:forEach>
            </ul>
        </c:if>

        <form action="${pageContext.request.contextPath}/roadmap" method="post" style="margin-top:20px;">
            <input type="hidden" name="action" value="generate">
            <button type="submit" class="secondary">다시 생성 (재분석 반영)</button>
        </form>
            </c:otherwise>
        </c:choose>
        </div>
        <div class="side">
            <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
        </div>
        </div>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
