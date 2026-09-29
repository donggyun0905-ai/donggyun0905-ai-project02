<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로드맵 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
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

        <c:if test="${progress.journeyComplete}">
            <div class="banner">🎉 지금까지 분석된 부족 기술을 모두 채웠습니다! 새로 재분석하면 다음 목표가 이어집니다.</div>
        </c:if>

        <%-- 상자 하나(.journey-map) 안에 완료한 것(위쪽, 흐리게)과 지금 할 일(아래쪽, 선명하게)을
             전부 같은 스크롤 트랙에 이어서 넣는다 — 박스를 따로 두 개 만들지 말고 하나로
             합쳐달라는 요청(사용자, 2026-09-29). 잠긴 미래 티어는 여전히 안 보여준다. --%>
        <c:set var="currentTier" value="${progress.currentTier}" />
        <c:set var="currentTierLabel"
               value="${currentTier.tier == 'ENTRY' ? '입문' : currentTier.tier == 'CORE' ? '핵심' : currentTier.tier == 'ADVANCED' ? '심화' : '전문가'}" />
        <div class="card journey-map">
            <h2 style="margin-bottom:2px;">
                <c:choose>
                    <c:when test="${progress.journeyComplete}">🧭 여정 기록</c:when>
                    <c:otherwise>🧭 여정 · 지금 할 일 (${currentTierLabel})</c:otherwise>
                </c:choose>
            </h2>
            <c:if test="${not progress.journeyComplete}">
                <div class="progress-track"><div class="progress-fill" style="width:${currentTier.percent}%;"></div></div>
            </c:if>
            <p class="muted" style="margin:2px 0 0;">박스 안에서 위아래로 스크롤하면 지나온 길과 지금 할 일을 이어서 볼 수 있어요.</p>
            <div class="journey-legend">
                <span><span class="dot completed"></span>완료</span>
                <span><span class="dot current"></span>지금 할 일</span>
                <span><span class="dot remaining"></span>남음</span>
            </div>

            <div class="journey-track-scroll">
            <div class="journey-track" style="margin-top:16px;">
                <c:set var="foundCurrent" value="false" scope="page" />
                <c:set var="rowIndex" value="0" scope="page" />
                <c:forEach var="step" items="${steps}">
                    <%-- 완료한 건 티어 상관없이 전부, 미완료는 지금 열린 티어만(잠긴 미래 티어 제외) --%>
                    <c:if test="${step.completed || (not progress.journeyComplete && step.tier == currentTier.tier)}">
                        <c:set var="rowIndex" value="${rowIndex + 1}" scope="page" />
                        <c:choose>
                            <c:when test="${step.completed}">
                                <c:set var="markerClass" value="completed is-past" />
                                <c:set var="markerIcon" value="✓" />
                                <c:set var="cardClass" value="is-past" />
                            </c:when>
                            <c:when test="${!foundCurrent}">
                                <c:set var="markerClass" value="current" />
                                <c:set var="markerIcon" value="⚓" />
                                <c:set var="cardClass" value="" />
                                <c:set var="foundCurrent" value="true" scope="page" />
                            </c:when>
                            <c:otherwise>
                                <c:set var="markerClass" value="remaining" />
                                <c:set var="markerIcon" value="" />
                                <c:set var="cardClass" value="" />
                            </c:otherwise>
                        </c:choose>

                        <div class="journey-row">
                            <div class="journey-marker ${markerClass}">${markerIcon}</div>
                            <div class="journey-card ${cardClass}" style="grid-column: ${rowIndex % 2 == 1 ? 1 : 3};">
                                <div class="row" style="margin-bottom:6px;">
                                    <span class="chip chip-teal">
                                        <c:choose>
                                            <c:when test="${step.stepType == 'CERT'}">자격증</c:when>
                                            <c:when test="${step.stepType == 'PROJECT'}">프로젝트</c:when>
                                            <c:otherwise>기술</c:otherwise>
                                        </c:choose>
                                    </span>
                                    <c:if test="${step.completed}"><span style="color:var(--teal); font-weight:bold; font-size:0.85rem;">✔ 완료</span></c:if>
                                </div>
                                <p>${step.reason}</p>
                                <c:choose>
                                    <c:when test="${step.completed}">
                                        <c:if test="${step.stepType != 'PROJECT'}">
                                            <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                                <input type="hidden" name="action" value="complete">
                                                <input type="hidden" name="stepId" value="${step.id}">
                                                <input type="hidden" name="completed" value="false">
                                                <button type="submit" class="link-button">완료 취소</button>
                                            </form>
                                        </c:if>
                                    </c:when>
                                    <c:when test="${step.stepType == 'PROJECT'}">
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
                                    </c:when>
                                    <c:otherwise>
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <input type="hidden" name="completed" value="true">
                                            <button type="submit">완료 체크</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:if>
                </c:forEach>
            </div>
            </div>
        </div>

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
