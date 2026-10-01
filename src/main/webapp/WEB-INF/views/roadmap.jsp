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

        <%-- "로드맵이 한 번 만들면 고정되는 문제" 해결(2026-09-30 팀 결정) — 목표 직무의 요구 기술이
             실제로 바뀌었을 때만(JOB.requirement_version 비교, 비용 0원) 배너를 띄운다. 사용자가
             직접 눌러야만 재분석(비용 발생)이 일어난다. --%>
        <c:if test="${requirementOutdated}">
            <div class="banner">
                🔔 목표 직무의 요구 기술이 바뀌었어요. 반영하면 로드맵이 새로 갱신됩니다.
                <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form" style="margin-top:6px;">
                    <input type="hidden" name="action" value="reanalyzeAndRegenerate">
                    <button type="submit">재분석하고 반영</button>
                </form>
            </div>
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
                <c:forEach var="step" items="${steps}">
                    <%-- 완료한 건 티어 상관없이 전부, 미완료는 지금 열린 티어 + 바로 다음 잠긴 티어까지 --%>
                    <c:if test="${step.completed
                                  || (not progress.journeyComplete && step.tier == currentTier.tier)
                                  || (not empty nextLockedTier && step.tier == nextLockedTier.tier)}">
                        <c:set var="rowIndex" value="${rowIndex + 1}" scope="page" />
                        <c:choose>
                            <c:when test="${step.completed}">
                                <c:set var="markerClass" value="completed is-past" />
                                <c:set var="markerIcon" value="✓" />
                                <c:set var="cardClass" value="is-past" />
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

                        <div class="journey-row" data-step-id="${step.id}">
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
                                <c:if test="${markerClass == 'locked'}">
                                    <p class="muted" style="margin:0; font-size:0.82rem;">지금 할 일을 다 끝내면 완료 체크를 할 수 있게 풀립니다.</p>
                                </c:if>
                                <c:if test="${markerClass != 'locked'}">
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
                                        <%-- [TEST] 파일 없이 통과 — 테스트할 때마다 파일을 매번 첨부하기 번거로워서 다시 추가함
                                             (2026-09-30, 사용자 요청). 실제 운영 배포 전에는 반드시 지울 것. --%>
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <input type="hidden" name="completed" value="true">
                                            <button type="submit" class="link-button">[TEST] 파일 없이 통과</button>
                                        </form>
                                    </c:when>
                                    <%-- ENTRY(공부노트)/EXPERT(기술 설명 글) SKILL 단계 — 규칙 기반 자동 판정(2026-09-30 팀 결정).
                                         미통과(NEEDS_REVISION)면 review_note를 보여주고 다시 제출할 수 있게 한다. --%>
                                    <c:when test="${step.stepType == 'SKILL' && (step.tier == 'ENTRY' || step.tier == 'EXPERT')}">
                                        <c:if test="${step.reviewStatus == 'NEEDS_REVISION'}">
                                            <p class="error-message" style="font-size:0.85rem; margin:6px 0;">📝 ${step.reviewNote}</p>
                                        </c:if>
                                        <details>
                                            <summary>${step.tier == 'EXPERT' ? '기술 설명 글 PDF 제출하기' : '공부노트 PDF 제출하기'}</summary>
                                            <form action="${pageContext.request.contextPath}/roadmap" method="post"
                                                  enctype="multipart/form-data" style="margin-top:10px;">
                                                <input type="hidden" name="action" value="submitSkillNote">
                                                <input type="hidden" name="stepId" value="${step.id}">
                                                <p>
                                                    <label>
                                                        <c:choose>
                                                            <c:when test="${step.tier == 'EXPERT'}">기술 설명 글 PDF (800자 이상 · 기술명 3회 이상 · 외부 링크 1개 이상)</c:when>
                                                            <c:otherwise>공부노트 PDF (300자 이상 · 기술명 2회 이상 · 코드 블록(```) 1개 이상)</c:otherwise>
                                                        </c:choose>
                                                    </label>
                                                    <input type="file" name="file" accept="application/pdf" required>
                                                </p>
                                                <button type="submit">제출하기</button>
                                            </form>
                                        </details>
                                        <%-- [TEST] 파일 없이 통과 — 테스트할 때마다 PDF를 매번 만들어 첨부하기 번거로워서
                                             다시 추가함(2026-09-30, 사용자 요청). action=complete를 그대로 재사용해서
                                             review_status/proof_content 없이 바로 완료 처리한다(규칙 판정 자체는 건너뜀).
                                             실제 운영 배포 전에는 반드시 지울 것. --%>
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <input type="hidden" name="completed" value="true">
                                            <button type="submit" class="link-button">[TEST] 파일 없이 통과</button>
                                        </form>
                                    </c:when>
                                    <%-- CORE/ADVANCED SKILL 단계 — 프로젝트 등록 또는 기존 프로젝트 업그레이드 + 증빙 파일로
                                         자동 확인(2026-09-30 팀 결정). userProjects는 RoadmapServlet에서 미리 담아준다.
                                         ADVANCED는 "심화" 단계 취지상 반드시 기존 프로젝트 업그레이드만 인정한다(팀 확정,
                                         2026-09-30) — 신규 프로젝트 선택지를 아예 안 보여주고, 업그레이드할 프로젝트가
                                         하나도 없으면 CORE부터 먼저 하라고 안내한다. --%>
                                    <c:when test="${step.stepType == 'SKILL' && (step.tier == 'CORE' || step.tier == 'ADVANCED')}">
                                        <c:choose>
                                            <c:when test="${step.tier == 'ADVANCED' && empty userProjects}">
                                                <p class="muted" style="font-size:0.85rem;">ADVANCED는 기존 프로젝트를 업그레이드해야 완료할 수 있어요. 먼저 CORE 단계에서 프로젝트를 하나 등록해주세요.</p>
                                            </c:when>
                                            <c:otherwise>
                                                <details>
                                                    <summary>프로젝트 등록/업그레이드하고 완료하기</summary>
                                                    <form action="${pageContext.request.contextPath}/roadmap" method="post"
                                                          enctype="multipart/form-data" style="margin-top:10px;">
                                                        <input type="hidden" name="action" value="submitSkillProject">
                                                        <input type="hidden" name="stepId" value="${step.id}">
                                                        <c:if test="${not empty userProjects}">
                                                            <p><label>기존 프로젝트 업그레이드${step.tier == 'CORE' ? ' (선택)' : ''}</label>
                                                                <select name="upgradeFromProjectId" ${step.tier == 'ADVANCED' ? 'required' : ''}>
                                                                    <c:if test="${step.tier == 'CORE'}">
                                                                        <option value="">-- 신규 프로젝트 --</option>
                                                                    </c:if>
                                                                    <c:forEach var="p" items="${userProjects}">
                                                                        <option value="${p.id}">${p.title}</option>
                                                                    </c:forEach>
                                                                </select>
                                                            </p>
                                                        </c:if>
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
                                                <%-- [TEST] 파일 없이 통과 — 다시 추가함(2026-09-30, 사용자 요청). ADVANCED의
                                                     "업그레이드 필수" 검증도 이걸로는 건너뛴다 — 테스트 전용이라 상관없음.
                                                     실제 운영 배포 전에는 반드시 지울 것. --%>
                                                <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                                    <input type="hidden" name="action" value="complete">
                                                    <input type="hidden" name="stepId" value="${step.id}">
                                                    <input type="hidden" name="completed" value="true">
                                                    <button type="submit" class="link-button">[TEST] 파일 없이 통과</button>
                                                </form>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:when>
                                    <%-- CERT 단계 — 자격증 취득 증빙 서류(합격 확인서·자격증 사진 등) 첨부로 완료
                                         (2026-09-30 팀 결정). 별도 규칙 판정 없이 첨부 자체를 신뢰한다. --%>
                                    <c:otherwise>
                                        <details>
                                            <summary>증빙 서류 첨부하고 완료하기</summary>
                                            <%-- 서류 내용은 검증하지 않고 첨부 자체를 신뢰하는 대신(팀 결정),
                                                 제출 전에 면접관 공유 화면에 그대로 노출된다는 걸 분명히 알린다
                                                 (2026-10-01 사용자 요청). --%>
                                            <form action="${pageContext.request.contextPath}/roadmap" method="post"
                                                  enctype="multipart/form-data" style="margin-top:10px;"
                                                  onsubmit="return confirm('이 자격증 문서는 면접관 공유 화면에 그대로 노출됩니다. 제출하시겠습니까?');">
                                                <input type="hidden" name="action" value="submitCertProof">
                                                <input type="hidden" name="stepId" value="${step.id}">
                                                <p><label>증빙 서류 (합격 확인서·자격증 사진 등)</label><input type="file" name="file" required></p>
                                                <button type="submit">제출하고 완료하기</button>
                                            </form>
                                        </details>
                                        <%-- [TEST] 파일 없이 통과 — 다른 단계들과 동일하게 테스트 편의용으로 추가함
                                             (2026-09-30, 사용자 요청). action=complete는 CERT 단계에 대해 서버에서
                                             거부하도록 막아뒀으므로(진짜 증빙 요구가 이번 요청의 핵심), submitCertProof에
                                             testShortcut 파라미터를 별도로 둬서 더미 증빙으로 대체한다.
                                             실제 운영 배포 전에는 반드시 지울 것. --%>
                                        <form action="${pageContext.request.contextPath}/roadmap" method="post" class="inline-form">
                                            <input type="hidden" name="action" value="submitCertProof">
                                            <input type="hidden" name="stepId" value="${step.id}">
                                            <input type="hidden" name="testShortcut" value="1">
                                            <button type="submit" class="link-button">[TEST] 파일 없이 통과</button>
                                        </form>
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

<%-- 티어 돌파 환영 모달(2026-10-01) — 방금 이 요청으로 티어가 100% 완료됐을 때만 서버가
     celebrateTier를 넘겨준다(세션에 한 번 실었다가 꺼내며 지우므로 새로고침하면 다시 안 뜬다).
     공통 CSS는 A 담당 파일이라 이 모달 스타일은 이 화면 안에 둔다. --%>
<c:if test="${not empty celebrateTier}">
    <c:set var="celebrateLabel"
           value="${celebrateTier == 'ENTRY' ? '입문' : celebrateTier == 'CORE' ? '핵심' : celebrateTier == 'ADVANCED' ? '심화' : '전문가'}" />
    <c:set var="celebrateNextLabel"
           value="${celebrateNextTier == 'ENTRY' ? '입문' : celebrateNextTier == 'CORE' ? '핵심' : celebrateNextTier == 'ADVANCED' ? '심화' : '전문가'}" />
    <style>
        .tier-modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center; padding: 16px; }
        .tier-modal { background: var(--card-bg); border-radius: var(--radius); padding: 28px 32px;
            max-width: 420px; width: 100%; text-align: center; box-shadow: 0 10px 40px rgba(0,0,0,0.3); }
        .tier-modal .big { font-size: 3rem; }
    </style>
    <div class="tier-modal-backdrop" id="tierModal" role="dialog" aria-modal="true" aria-labelledby="tierModalTitle">
        <div class="tier-modal">
            <div class="big">🎉</div>
            <h2 id="tierModalTitle">${celebrateLabel} 티어 돌파!</h2>
            <c:choose>
                <c:when test="${not empty celebrateNextTier}">
                    <p>${celebrateLabel} 티어의 모든 단계를 완료했어요.<br><strong>${celebrateNextLabel}</strong> 티어가 새로 열렸습니다.</p>
                </c:when>
                <c:otherwise>
                    <p>${celebrateLabel} 티어의 모든 단계를 완료했어요.<br>지금까지 분석된 부족 기술을 모두 채웠습니다!</p>
                </c:otherwise>
            </c:choose>
            <button type="button" id="tierModalClose">계속하기</button>
        </div>
    </div>
    <script>
    (function () {
        var modal = document.getElementById('tierModal');
        function close() { modal.parentNode.removeChild(modal); }
        document.getElementById('tierModalClose').addEventListener('click', close);
        modal.addEventListener('click', function (e) { if (e.target === modal) { close(); } });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && modal.parentNode) { close(); } });
    })();
    </script>
</c:if>

<%-- 완료 체크 등 폼 제출은 전부 전체 페이지 리로드라, 매번 "여정 기록" 스크롤이 맨 위로 튕겨서
     방금 작업하던 위치를 잃어버리는 문제(사용자, 2026-09-30). 처음엔 스크롤 위치를 픽셀 값
     그대로 저장/복원했는데(sessionStorage), 완료 처리로 티어가 새로 열리거나 카드 배치가
     바뀌면 그 사이 전체 높이가 달라져서 저장해둔 픽셀 위치가 더 이상 맞지 않아 엉뚱한 곳으로
     튕기는 문제가 남아있었다(2026-10-01 재확인). 픽셀 대신 "방금 작업한 단계가 어떤
     step.id였는지"를 저장했다가, 그 단계의 카드(`[data-step-id]`)를 다시 찾아 컨테이너
     안에서만 보이는 위치로 스크롤한다 — 콘텐츠 높이가 바뀌어도 기준이 "그 카드"라서 흔들리지
     않는다. 서버 로직 변경 없이 화면 스크립트만으로 해결, sessionStorage라 새 탭엔 영향 없다. --%>
<script>
(function () {
    var STORAGE_KEY = 'roadmapLastStepId';
    var container = document.getElementById('journeyScroll');
    if (container) {
        var lastStepId = sessionStorage.getItem(STORAGE_KEY);
        if (lastStepId !== null) {
            var target = container.querySelector('[data-step-id="' + lastStepId + '"]');
            if (target) {
                var containerRect = container.getBoundingClientRect();
                var targetRect = target.getBoundingClientRect();
                // target을 컨테이너 중앙 부근에 오도록 — scrollIntoView는 바깥 페이지까지
                // 같이 스크롤시킬 수 있어서, 이 컨테이너 안에서만 scrollTop을 직접 계산한다.
                container.scrollTop += (targetRect.top - containerRect.top) - (containerRect.height / 2);
            }
            sessionStorage.removeItem(STORAGE_KEY);
        }
    }
    document.addEventListener('submit', function (e) {
        var stepIdField = e.target.querySelector && e.target.querySelector('input[name="stepId"]');
        if (stepIdField && stepIdField.value) {
            sessionStorage.setItem(STORAGE_KEY, stepIdField.value);
        } else {
            // "다시 생성", "재분석하고 반영"처럼 특정 단계와 무관한 제출은 복원 기준이 없으니
            // 지난 값이 남아 엉뚱하게 복원되지 않도록 지운다.
            sessionStorage.removeItem(STORAGE_KEY);
        }
    }, true);
})();
</script>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
