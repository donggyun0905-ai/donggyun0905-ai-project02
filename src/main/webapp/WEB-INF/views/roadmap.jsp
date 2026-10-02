<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="로드맵 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />
<%@ include file="/WEB-INF/views/roadmap/_styles.jspf" %>

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

        <c:if test="${not empty roadmapNotice}">
            <p style="background:var(--teal-bg); color:var(--teal); border-radius:6px; padding:10px 14px;"><c:out value="${roadmapNotice}" /></p>
        </c:if>
        <c:if test="${not empty errorMessage}">
            <p class="error-message">${errorMessage}</p>
        </c:if>

        <%-- "로드맵이 한 번 만들면 고정되는 문제" 해결(2026-09-30 팀 결정) — 목표 직무의 요구 기술이
             실제로 바뀌었을 때만(JOB.requirement_version 비교, 비용 0원) 배너를 띄운다. 사용자가
             직접 눌러야만 재분석(비용 발생)이 일어난다. --%>
        <c:if test="${requirementOutdated}">
            <div class="banner">
                🔔 목표 직무의 요구 기술이 바뀌었어요. 반영하면 로드맵이 새로 갱신됩니다.
                <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form" style="margin-top:6px;">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="reanalyzeAndRegenerate">
                    <button type="submit">재분석하고 반영</button>
                </form>
            </div>
        </c:if>

        <div class="roadmap-layout">
        <aside class="rm-left">
            <jsp:include page="/WEB-INF/views/common/roadmap-left-widgets.jsp" />
        </aside>
        <div class="rm-center">
        <c:choose>
            <c:when test="${empty roadmap}">
                <div class="card">
                    <p>아직 생성된 로드맵이 없습니다. 먼저 격차 분석을 완료해야 만들 수 있습니다.</p>
                    <form action="${pageContext.request.contextPath}/roadmap" method="post">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
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
                        <c:when test="${t.emptyTier}">${tierLabel} · 해당 없음 (남은 부족 기술 없음)</c:when>
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
        <div class="journey-map">
            <h2 style="margin-bottom:2px;">
                <c:choose>
                    <c:when test="${progress.journeyComplete}">🧭 여정 기록</c:when>
                    <c:otherwise>🧭 여정 · 지금 할 일 (${currentTierLabel})</c:otherwise>
                </c:choose>
            </h2>
            <c:if test="${not progress.journeyComplete}">
                <div class="progress-track"><div class="progress-fill" style="width:${currentTier.percent}%;"></div></div>
            </c:if>
            <p class="muted" style="margin:2px 0 0;">아래로 내려가며 지나온 길(흰 길)과 지금 할 일을 이어서 볼 수 있어요.</p>
            <div class="journey-legend">
                <span><span class="dot completed"></span>완료</span>
                <span><span class="dot current"></span>지금 할 일</span>
                <span><span class="dot remaining"></span>남음</span>
                <span><span class="dot locked"></span>다음 단계(잠김)</span>
            </div>

            <%-- 다음 단계 미리보기: db-design.md 원 취지("현재 tier + 다음 tier까지 노출, 나머지는
                 흐리게")가 어제 작업에서 너무 엄격하게(잠긴 티어 전부 숨김) 바뀌어 있었다 —
                 "로드맵(여정 전체를 보여준다)" 정체성과 안 맞아서, 바로 다음 잠긴 티어 하나만
                 흐리게(opacity) 미리 보여주도록 되돌린다(2026-09-30, 집 PC 작업). 액션 버튼은
                 없다 — 잠긴 단계는 완료할 수 없다. --%>
            <c:set var="nextLockedTier" value="${progress.nextLockedTier}" />
            <div class="journey-track-scroll" id="journeyScroll">
            <div class="journey-track" style="margin-top:16px;">
                <c:set var="foundCurrent" value="false" scope="page" />
                <c:set var="rowIndex" value="0" scope="page" />
                <c:if test="${hiddenCompletedCount > 0}">
                    <p class="muted" style="text-align:center; margin:0 0 10px;"><a href="${pageContext.request.contextPath}/roadmap?history=all">이전 완료 기록 ${hiddenCompletedCount}개 더 보기</a></p>
                </c:if>
                <c:if test="${historyAll && historyCollapsible}">
                    <p class="muted" style="text-align:center; margin:0 0 10px;"><a href="${pageContext.request.contextPath}/roadmap">최근 기록만 보기</a></p>
                </c:if>
                <c:forEach var="step" items="${steps}">
                    <%-- 완료한 건 티어 상관없이 전부, 미완료는 지금 열린 티어 + 바로 다음 잠긴 티어까지 --%>
                    <c:if test="${step.completed || step.upkeep
                                  || (not progress.journeyComplete && step.tier == currentTier.tier)
                                  || (not empty nextLockedTier && step.tier == nextLockedTier.tier)}">
                        <c:set var="rowIndex" value="${rowIndex + 1}" scope="page" />
                        <c:choose>
                            <c:when test="${step.completed}">
                                <c:set var="markerClass" value="completed is-past" />
                                <c:set var="markerIcon" value="✓" />
                                <c:set var="cardClass" value="is-past" />
                            </c:when>
                            <%-- 복습·업데이트·트렌딩 학습은 시간이 지나 생기는 단계라 잠그지 않고 바로 할 수 있게 둔다(끝없는 로드맵) --%>
                            <c:when test="${step.upkeep}">
                                <c:set var="markerClass" value="remaining" />
                                <c:set var="markerIcon" value="${step.stepType == 'REVIEW' ? '🔁' : step.stepType == 'PROJECT_UPDATE' ? '🛠' : step.stepType == 'ARTICLE_UPDATE' ? '📝' : '📈'}" />
                                <c:set var="cardClass" value="" />
                            </c:when>
                            <c:when test="${not empty nextLockedTier && step.tier == nextLockedTier.tier}">
                                <c:set var="markerClass" value="locked" />
                                <c:set var="markerIcon" value="🔒" />
                                <c:set var="cardClass" value="is-locked" />
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

                        <div class="journey-row ${rowIndex % 2 == 1 ? 'card-left' : 'card-right'}" data-step-id="${step.id}">
                            <div class="journey-marker ${markerClass}">${markerIcon}</div>
                            <div class="journey-card ${cardClass}" style="grid-column: ${rowIndex % 2 == 1 ? 1 : 3};">
                                <div class="row" style="margin-bottom:6px;">
                                    <span class="chip chip-teal">
                                        <c:choose>
                                            <c:when test="${step.stepType == 'CERT'}">자격증</c:when>
                                            <c:when test="${step.stepType == 'PROJECT'}">프로젝트</c:when>
                                            <c:when test="${step.stepType == 'REVIEW'}">복습</c:when>
                                            <c:when test="${step.stepType == 'PROJECT_UPDATE'}">프로젝트 업데이트</c:when>
                                            <c:when test="${step.stepType == 'ARTICLE_UPDATE'}">기술 글 업데이트</c:when>
                                            <c:when test="${step.stepType == 'TREND_STUDY'}">트렌딩 학습</c:when>
                                            <c:otherwise>기술</c:otherwise>
                                        </c:choose>
                                    </span>
                                    <c:if test="${step.completed}"><span style="color:var(--teal); font-weight:bold; font-size:0.85rem;">✔ 완료</span></c:if>
                                </div>
                                <%-- "💡 제목 — 긴 설명" 형태의 프로젝트 아이디어는 카드에 제목만 두고, 설명은 박스를 눌렀을 때 뜨는 창에 보여준다. --%>
                                <c:set var="ideaSplit" value="${fn:startsWith(step.reason, '💡') && fn:contains(step.reason, ' — ')}" scope="page" />
                                <c:choose>
                                    <c:when test="${ideaSplit}">
                                        <c:set var="ideaTitle" value="${fn:substringBefore(step.reason, ' — ')}" scope="page" />
                                        <c:set var="ideaDesc" value="${fn:substringAfter(step.reason, ' — ')}" scope="page" />
                                        <p>${ideaTitle}</p>
                                    </c:when>
                                    <c:otherwise>
                                        <c:set var="ideaDesc" value="" scope="page" />
                                        <p>${step.reason}</p>
                                    </c:otherwise>
                                </c:choose>
                                <c:if test="${markerClass == 'locked'}">
                                    <p class="muted" style="margin:0; font-size:0.82rem;">지금 할 일을 다 끝내면 완료 체크를 할 수 있게 풀립니다.</p>
                                </c:if>
                                <c:if test="${markerClass != 'locked'}">
                                <c:choose>
                                    <c:when test="${step.completed && step.upkeep}">
                                        <%-- 복습·업데이트·트렌딩 학습은 완료 취소가 없다 — 점수를 받은 기록이라 되돌리지 않는다 --%>
                                    </c:when>
                                    <c:when test="${step.completed}">
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form cancel-step"
                                              data-project="${step.stepType == 'PROJECT'}">
                                            <input type="hidden" name="_csrf" value="${csrfToken}">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <input type="hidden" name="completed" value="false">
                                            <button type="submit" class="link-button">완료 취소</button>
                                        </form>
                                    </c:when>
                                    <c:when test="${step.stepType == 'REVIEW'}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-review.jspf" %>
                                    </c:when>
                                    <c:when test="${step.stepType == 'PROJECT_UPDATE' || step.stepType == 'TREND_STUDY'}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-upkeep-note.jspf" %>
                                    </c:when>
                                    <c:when test="${step.stepType == 'ARTICLE_UPDATE'}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-article-update.jspf" %>
                                    </c:when>
                                    <c:when test="${step.stepType == 'PROJECT'}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-project.jspf" %>
                                    </c:when>
                                    <%-- ENTRY(공부노트)/EXPERT(기술 설명 글) SKILL 단계 — 규칙 기반 자동 판정(2026-09-30 팀 결정).
                                         미통과(NEEDS_REVISION)면 review_note를 보여주고 다시 제출할 수 있게 한다. --%>
                                    <c:when test="${step.stepType == 'SKILL' && (step.tier == 'ENTRY' || step.tier == 'EXPERT')}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-skill-note.jspf" %>
                                    </c:when>
                                    <%-- CORE/ADVANCED SKILL 단계 — 프로젝트 등록 또는 기존 프로젝트 업그레이드 + 증빙 파일로
                                         자동 확인(2026-09-30 팀 결정). userProjects는 RoadmapServlet에서 미리 담아준다.
                                         ADVANCED는 "심화" 단계 취지상 반드시 기존 프로젝트 업그레이드만 인정한다(팀 확정,
                                         2026-09-30) — 신규 프로젝트 선택지를 아예 안 보여주고, 업그레이드할 프로젝트가
                                         하나도 없으면 CORE부터 먼저 하라고 안내한다. --%>
                                    <c:when test="${step.stepType == 'SKILL' && (step.tier == 'CORE' || step.tier == 'ADVANCED')}">
<%@ include file="/WEB-INF/views/roadmap/_step-action-skill-project.jspf" %>
                                    </c:when>
                                    <%-- CERT 단계 — 자격증 취득 증빙 서류(합격 확인서·자격증 사진 등) 첨부로 완료
                                         (2026-09-30 팀 결정). 별도 규칙 판정 없이 첨부 자체를 신뢰한다. --%>
                                    <c:otherwise>
<%@ include file="/WEB-INF/views/roadmap/_step-action-cert.jspf" %>
                                    </c:otherwise>
                                </c:choose>
                                </c:if>
                            </div>
                        </div>
                    </c:if>
                </c:forEach>
            </div>
            </div>
        </div>

        <form action="${pageContext.request.contextPath}/roadmap" method="post" style="margin-top:20px;">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="generate">
            <button type="submit" class="secondary">다시 생성 (재분석 반영)</button>
        </form>
            </c:otherwise>
        </c:choose>
        </div>
        <aside class="rm-right">
            <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
            <jsp:include page="/WEB-INF/views/common/roadmap-note-widget.jsp" />
        </aside>
        </div>
    </c:otherwise>
</c:choose>

<%@ include file="/WEB-INF/views/roadmap/_celebration.jspf" %>

<%@ include file="/WEB-INF/views/roadmap/_scripts.jspf" %>

<%@ include file="/WEB-INF/views/roadmap/_path.jspf" %>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
