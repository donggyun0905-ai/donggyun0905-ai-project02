<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="지원자 이력 - 스펙 오디세이" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<c:choose>
    <%-- 6-4. 유효하지 않은 공유 링크 --%>
    <c:when test="${not valid}">
        <div class="card empty-state" style="max-width:460px; margin:60px auto;">
            <div class="icon"><span class="ic ic-link" aria-hidden="true"></span></div>
            <h2>유효하지 않은 링크입니다</h2>
            <p class="muted">링크가 만료되었거나 지원자가 공유를 멈췄습니다. 이력을 보려면 지원자에게 새 링크를 요청해 주세요.</p>
            <a href="${pageContext.request.contextPath}/">스펙 오디세이 알아보기</a>
        </div>
    </c:when>
    <c:otherwise>
        <div class="spread" style="margin-bottom:16px;">
            <span class="pill"><span class="ic ic-eye" aria-hidden="true"></span> 읽기 전용 · 지원자가 공유한 이력</span>
            <c:choose>
                <c:when test="${interviewer}">
                    <form method="post" action="${pageContext.request.contextPath}/interviewer/shared">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="add">
                        <input type="hidden" name="link" value="<c:out value='${token}' />">
                        <button type="submit" class="secondary">비교 목록에 담기</button>
                    </form>
                </c:when>
                <c:when test="${empty sessionScope.loginUser}">
                    <a class="btn secondary" href="${pageContext.request.contextPath}/login">면접관 로그인 후 비교 목록에 담기</a>
                </c:when>
            </c:choose>
        </div>
        <h1>지원자 이력</h1>
        <p class="muted">지원자가 직접 발급한 링크로 열린 페이지입니다. 지원자가 고른 항목만 보이고, 지원자는 언제든 공유를 멈출 수 있습니다.</p>

        <div class="card">
            <c:if test="${view.scopeBasic}">
                <h2>기본 정보</h2>
                <div class="row" style="margin-top:10px;">
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">이름</div><strong><c:out value="${view.name}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">전공</div><strong><c:out value="${view.major}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">학년</div><strong><c:out value="${view.grade}" default="미입력" /></strong></span>
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">희망 직무</div><strong><c:out value="${view.desiredJobName}" default="미정" /></strong></span>
                </div>
            </c:if>
            <c:if test="${view.scopeAge}">
                <div class="row" style="margin-top:10px;">
                    <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">나이</div><strong><c:choose><c:when test="${empty view.age}">미입력</c:when><c:otherwise>${view.age}세</c:otherwise></c:choose></strong></span>
                </div>
            </c:if>
            <p style="margin-top:12px; margin-bottom:0;">
                공개된 항목
                <c:if test="${view.scopeBasic}"><span class="chip chip-teal">기본 이력</span></c:if>
                <c:if test="${view.scopeSkills}"><span class="chip chip-teal">보유 기술 스택</span></c:if>
                <c:if test="${view.scopeGrowth}"><span class="chip chip-teal">성장 잠재력</span></c:if>
                <c:if test="${view.scopeResume}"><span class="chip chip-teal">이력서 파일</span></c:if>
                <c:if test="${view.scopeCoverLetter}"><span class="chip chip-teal">자소서 파일</span></c:if>
                <c:if test="${view.scopeAge}"><span class="chip chip-teal">나이</span></c:if>
                <c:if test="${view.scopeActivity}"><span class="chip chip-teal">활동 내역</span></c:if>
                <c:if test="${view.scopeEducation}"><span class="chip chip-teal">학력</span></c:if>
                <c:if test="${view.scopeProjectDocs}"><span class="chip chip-teal">프로젝트 서류</span></c:if>
            </p>
        </div>

        <%-- 학력 — 지원자가 링크에 "학력"을 고른 경우에만 (블라인드 채용 고려) --%>
        <c:if test="${view.scopeEducation}">
            <div class="card">
                <h2>학력</h2>
                <c:choose>
                    <c:when test="${empty view.education}">
                        <p class="muted" style="margin-top:10px; margin-bottom:0;">지원자가 아직 학력을 입력하지 않았습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <div class="row" style="margin-top:10px;">
                            <span style="flex:2;"><div class="muted" style="font-size:0.8rem;">학교</div><strong><c:out value="${view.education.schoolName}" /></strong></span>
                            <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">상태</div><strong><c:out value="${view.education.statusLabel}" /></strong></span>
                            <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">졸업(예정)일</div><strong><c:out value="${view.education.graduationDate}" default="미입력" /></strong></span>
                            <span style="flex:1;"><div class="muted" style="font-size:0.8rem;">학점</div><strong><c:out value="${view.education.gpaText}" default="미입력" /></strong></span>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- 이력서·자소서는 한 카드에 모아서 한눈에 보고 바로 내려받게 한다. PDF면 카드 안에서 원본을 바로 본다 --%>
        <c:if test="${view.scopeResume || view.scopeCoverLetter}">
            <div class="card">
                <h2>이력서 · 자소서</h2>
                <c:if test="${view.scopeResume}">
                    <c:choose>
                        <c:when test="${empty view.resumeFileName}">
                            <p class="muted" style="margin-top:10px; margin-bottom:0;">이력서 — 지원자가 아직 올리지 않았습니다.</p>
                        </c:when>
                        <%-- DB에는 등록돼 있는데 이 서버에 파일이 없는 경우. 업로드 폴더는 서버 PC마다 따로라
                             팀원이 올린 서류는 공유 DB에 행만 남는다. 열리지 않는 미리보기를 그리는 대신 이유를 알린다. --%>
                        <c:when test="${not view.resumeFileReadable}">
                            <p style="margin-top:10px; margin-bottom:0;">이력서 · <c:out value="${view.resumeFileName}" /></p>
                            <p class="muted" style="margin-top:6px; margin-bottom:0; font-size:0.84rem;">이 서버에서 파일을 찾을 수 없어 미리보기와 내려받기를 할 수 없습니다. 지원자에게 다시 올려 달라고 요청해 주세요.</p>
                        </c:when>
                        <c:otherwise>
                            <p style="margin-top:10px; margin-bottom:0;">
                                이력서 · <a href="${pageContext.request.contextPath}/share/${token}/resume" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${view.resumeFileName}" /> 내려받기</a>
                                <c:if test="${view.resumePdf}">
                                    · <a href="${pageContext.request.contextPath}/share/${token}/resume?view=inline" target="_blank" rel="noopener">새 창에서 보기</a>
                                </c:if>
                            </p>
                            <%-- PDF는 브라우저 내장 뷰어로 원본 그대로 보여준다. DOCX·HWP는 브라우저가 그리지 못해 내려받기만 --%>
                            <c:choose>
                                <c:when test="${view.resumePdf}">
                                    <iframe src="${pageContext.request.contextPath}/share/${token}/resume?view=inline" title="이력서 원본"
                                            style="display:block; width:100%; height:80vh; min-height:560px; margin-top:10px; border:1px solid var(--border); border-radius:var(--radius);"></iframe>
                                </c:when>
                                <c:otherwise>
                                    <p class="muted" style="margin-top:6px; margin-bottom:0; font-size:0.84rem;">PDF가 아닌 파일은 화면 미리보기를 지원하지 않습니다. 내려받아 확인해 주세요.</p>
                                </c:otherwise>
                            </c:choose>
                        </c:otherwise>
                    </c:choose>
                </c:if>
                <c:if test="${view.scopeCoverLetter}">
                    <c:choose>
                        <c:when test="${empty view.coverLetterFileName}">
                            <p class="muted" style="margin-top:10px; margin-bottom:0;">자소서 — 지원자가 아직 올리지 않았습니다.</p>
                        </c:when>
                        <%-- DB에는 등록돼 있는데 이 서버에 파일이 없는 경우. 업로드 폴더는 서버 PC마다 따로라
                             팀원이 올린 서류는 공유 DB에 행만 남는다. 열리지 않는 미리보기를 그리는 대신 이유를 알린다. --%>
                        <c:when test="${not view.coverLetterFileReadable}">
                            <p style="margin-top:16px; margin-bottom:0;">자소서 · <c:out value="${view.coverLetterFileName}" /></p>
                            <p class="muted" style="margin-top:6px; margin-bottom:0; font-size:0.84rem;">이 서버에서 파일을 찾을 수 없어 미리보기와 내려받기를 할 수 없습니다. 지원자에게 다시 올려 달라고 요청해 주세요.</p>
                        </c:when>
                        <c:otherwise>
                            <p style="margin-top:16px; margin-bottom:0;">
                                자소서 · <a href="${pageContext.request.contextPath}/share/${token}/cover-letter" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${view.coverLetterFileName}" /> 내려받기</a>
                                <c:if test="${view.coverLetterPdf}">
                                    · <a href="${pageContext.request.contextPath}/share/${token}/cover-letter?view=inline" target="_blank" rel="noopener">새 창에서 보기</a>
                                </c:if>
                            </p>
                            <c:choose>
                                <c:when test="${view.coverLetterPdf}">
                                    <iframe src="${pageContext.request.contextPath}/share/${token}/cover-letter?view=inline" title="자소서 원본"
                                            style="display:block; width:100%; height:80vh; min-height:560px; margin-top:10px; border:1px solid var(--border); border-radius:var(--radius);"></iframe>
                                </c:when>
                                <c:otherwise>
                                    <p class="muted" style="margin-top:6px; margin-bottom:0; font-size:0.84rem;">PDF가 아닌 파일은 화면 미리보기를 지원하지 않습니다. 내려받아 확인해 주세요.</p>
                                </c:otherwise>
                            </c:choose>
                        </c:otherwise>
                    </c:choose>
                </c:if>
            </div>
        </c:if>

        <%-- FR-81 이력 타임라인 --%>
        <c:if test="${view.scopeBasic}">
            <div class="card">
                <h2>이력 타임라인</h2>
                <c:choose>
                    <c:when test="${empty view.timeline}">
                        <p class="muted" style="margin-top:10px;">아직 등록된 자격증·어학·수상·경험·프로젝트가 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <table style="margin-top:10px;">
                            <c:forEach var="item" items="${view.timeline}">
                                <tr>
                                    <td class="muted" style="width:190px;"><c:out value="${item.dateText}" /></td>
                                    <td>
                                        <strong><c:out value="${item.typeLabel}" /></strong><br>
                                        <c:out value="${item.title}" />
                                        <c:if test="${not empty item.detail}">
                                            <br><span class="muted" style="font-size:0.84rem;"><c:out value="${item.detail}" /></span>
                                        </c:if>
                                        <c:if test="${not empty item.teamText}">
                                            <br><span style="font-size:0.84rem;"><strong>팀 · 역할</strong> <c:out value="${item.teamText}" /></span>
                                        </c:if>
                                        <c:if test="${not empty item.links}">
                                            <br><span style="font-size:0.84rem;"><span class="ic ic-link" aria-hidden="true"></span>
                                                <c:forEach var="link" items="${item.links}" varStatus="ls"><c:if test="${!ls.first}"> · </c:if><a href="<c:out value='${link.url}' />" target="_blank" rel="noopener noreferrer nofollow"><c:out value="${link.label}" /></a></c:forEach>
                                            </span>
                                        </c:if>
                                        <c:if test="${not empty item.documentId}">
                                            <br><a href="${pageContext.request.contextPath}/share/documents/${token}/${item.documentId}" target="_blank" style="font-size:0.84rem;">증빙 서류 보기</a>
                                        </c:if>
                                        <%-- 프로젝트 상세: 회고 · 기술별 활용 · 갖춘 서류 (파일은 "프로젝트 서류"를 공개한 링크에서만) --%>
                                        <c:if test="${item.hasProjectDetail}">
                                            <details style="margin-top:8px; font-size:0.88rem;">
                                                <summary style="cursor:pointer; color:var(--teal);">프로젝트 상세 보기</summary>
                                                <div style="margin-top:8px; padding:10px 12px; border-left:3px solid var(--border);">
                                                    <c:if test="${not empty item.retrospective}">
                                                        <div class="muted" style="font-size:0.8rem;">완료 회고 — 무엇을 배우고 해결했는지</div>
                                                        <p style="margin:4px 0 10px; white-space:pre-line;"><c:out value="${item.retrospective}" /></p>
                                                    </c:if>
                                                    <c:if test="${not empty item.techNotes}">
                                                        <div class="muted" style="font-size:0.8rem;">기술별 활용</div>
                                                        <ul style="margin:4px 0 10px; padding-left:18px;">
                                                            <c:forEach var="note" items="${item.techNotes}">
                                                                <li><strong><c:out value="${note.skillName}" /></strong> — <c:out value="${note.description}" /></li>
                                                            </c:forEach>
                                                        </ul>
                                                    </c:if>
                                                    <c:if test="${not empty item.submittedDocs}">
                                                        <div class="muted" style="font-size:0.8rem;">갖춘 서류<c:if test="${not view.scopeProjectDocs}"> — 지원자가 파일은 공개하지 않았습니다</c:if></div>
                                                        <c:choose>
                                                            <c:when test="${view.scopeProjectDocs}">
                                                                <%-- 지원자가 "프로젝트 서류"를 공개한 링크 — PDF·이미지는 펼치면 원본 그대로, 그 외는 내려받기 --%>
                                                                <c:forEach var="doc" items="${item.submittedDocs}">
                                                                    <c:set var="docUrl" value="${pageContext.request.contextPath}/share/documents/${token}/${doc.documentId}" />
                                                                    <c:choose>
                                                                        <c:when test="${doc.fileMissing}">
                                                                            <p style="margin:4px 0;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span>
                                                                                <c:out value="${doc.fileName}" />
                                                                                <span class="muted" style="font-size:0.82rem;">— 이 서버에서 파일을 찾을 수 없습니다</span></p>
                                                                        </c:when>
                                                                        <c:when test="${empty doc.documentId}">
                                                                            <p style="margin:4px 0;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span></p>
                                                                        </c:when>
                                                                        <c:when test="${not empty doc.previewType}">
                                                                            <details style="margin:4px 0;">
                                                                                <summary style="cursor:pointer;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span> <c:out value="${doc.fileName}" /> 미리보기</summary>
                                                                                <c:choose>
                                                                                    <c:when test="${doc.previewType == 'pdf'}">
                                                                                        <iframe src="<c:out value='${docUrl}' />" title="<c:out value='${doc.label}' />" loading="lazy"
                                                                                                style="display:block; width:100%; height:70vh; min-height:480px; margin-top:6px; border:1px solid var(--border); border-radius:var(--radius);"></iframe>
                                                                                    </c:when>
                                                                                    <c:otherwise>
                                                                                        <img src="<c:out value='${docUrl}' />" alt="<c:out value='${doc.label}' />" loading="lazy"
                                                                                             style="display:block; max-width:100%; margin-top:6px; border:1px solid var(--border); border-radius:var(--radius);">
                                                                                    </c:otherwise>
                                                                                </c:choose>
                                                                                <a href="<c:out value='${docUrl}' />" target="_blank" rel="noopener" style="font-size:0.84rem;">새 창에서 보기</a>
                                                                            </details>
                                                                        </c:when>
                                                                        <c:otherwise>
                                                                            <p style="margin:4px 0;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span>
                                                                                <a href="<c:out value='${docUrl}' />" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${doc.fileName}" /> 내려받기</a></p>
                                                                        </c:otherwise>
                                                                    </c:choose>
                                                                </c:forEach>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <p style="margin:4px 0 0;">
                                                                    <c:forEach var="doc" items="${item.submittedDocs}">
                                                                        <span class="chip chip-teal"><c:out value="${doc.label}" /></span>
                                                                    </c:forEach>
                                                                </p>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </c:if>
                                                </div>
                                            </details>
                                        </c:if>
                                    </td>
                                </tr>
                            </c:forEach>
                        </table>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- 보유 스펙 — 타임라인과 같은 데이터를 종류별로 묶어 보여 준다(2026-10-07 사용자 요청).
             타임라인은 프로젝트와 섞여 시간순이라 "자격증이 몇 개인지"를 훑을 수 없었다.
             새로 공개하는 값이 아니므로 공개 범위도 타임라인과 같다(scope_basic). --%>
        <c:if test="${view.scopeBasic and view.hasSpecs}">
            <div class="card">
                <h2>보유 스펙</h2>
                <c:forEach var="group" items="${view.specGroups}">
                    <div style="margin-top:12px;">
                        <div class="muted" style="font-size:0.82rem;"><c:out value="${group.key}" /> <strong>${fn:length(group.value)}</strong>건</div>
                        <table style="margin-top:4px;">
                            <c:forEach var="spec" items="${group.value}">
                                <tr>
                                    <td><c:out value="${spec.title}" />
                                        <c:if test="${not empty spec.detail}">
                                            <span class="muted" style="font-size:0.84rem;"> · <c:out value="${spec.detail}" /></span>
                                        </c:if>
                                        <c:if test="${not empty spec.documentId}">
                                            <a href="${pageContext.request.contextPath}/share/documents/${token}/${spec.documentId}" class="file-link" style="font-size:0.84rem;"><span class="ic ic-paperclip" aria-hidden="true"></span> 증빙</a>
                                        </c:if>
                                    </td>
                                    <td class="muted" style="width:190px; font-size:0.84rem;"><c:out value="${spec.dateText}" /></td>
                                </tr>
                            </c:forEach>
                        </table>
                    </div>
                </c:forEach>
            </div>
        </c:if>

        <c:if test="${view.scopeSkills}">
            <div class="card">
                <h2>보유 기술 스택</h2>
                <c:choose>
                    <c:when test="${empty view.skills}">
                        <p class="muted" style="margin-top:10px;">아직 등록된 기술이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <%-- 기술마다 "어디에 써 봤는지" 근거를 붙인다. 근거는 프로젝트 목록(기본 이력)도 공개한 링크에서만 --%>
                        <table style="margin-top:10px;">
                            <c:forEach var="skill" items="${view.skillItems}">
                                <tr>
                                    <td style="width:220px;"><span class="pill"><c:out value="${skill.label}" /></span></td>
                                    <td style="font-size:0.86rem;">
                                        <c:choose>
                                            <c:when test="${not empty skill.projectTitles}">
                                                사용한 프로젝트 ${skill.projectTitles.size()}개 ·
                                                <c:forEach var="title" items="${skill.projectTitles}" varStatus="ts"><c:if test="${!ts.first}">, </c:if><c:out value="${title}" /></c:forEach>
                                            </c:when>
                                            <c:when test="${view.scopeBasic}"><span class="muted">프로젝트에서 사용한 기록 없음</span></c:when>
                                            <c:otherwise><span class="muted">-</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </table>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- 활동 내역 (2026-10-07) — 날짜별 활동량 잔디 + 최근에 무엇을 했는지.
             지원자가 링크에서 "활동 내역"을 켠 경우에만 보인다(SHARE_LINK.scope_activity). --%>
        <c:if test="${view.scopeActivity}">
            <div class="card">
                <h2>활동 내역</h2>
                <c:choose>
                    <c:when test="${empty view.activity or view.activity.totalEvents == 0}">
                        <p class="muted" style="margin-top:10px;">아직 쌓인 활동 기록이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p class="muted" style="margin-top:4px;">
                            <c:out value="${view.activity.rangeText}" /> · 활동한 날 <strong>${view.activity.activeDays}일</strong>
                            · 전체 <strong>${view.activity.totalEvents}회</strong>
                        </p>
                        <div class="act-grid">
                            <c:forEach var="week" items="${view.activity.weeks}" varStatus="w">
                                <div class="act-week">
                                    <span class="act-month"><c:out value="${view.activity.monthLabels[w.index]}" /></span>
                                    <c:forEach var="cell" items="${week}">
                                        <span class="act-cell lv${cell.level}${cell.filler ? ' act-filler' : ''}"
                                              title="<c:out value='${cell.title}' />"></span>
                                    </c:forEach>
                                </div>
                            </c:forEach>
                        </div>
                        <div class="act-legend">
                            <span class="muted">적음</span>
                            <span class="act-cell lv0"></span><span class="act-cell lv1"></span>
                            <span class="act-cell lv2"></span><span class="act-cell lv3"></span>
                            <span class="act-cell lv4"></span>
                            <span class="muted">많음</span>
                        </div>

                        <c:if test="${not empty view.activity.timeline}">
                            <h3 style="margin:18px 0 8px; font-size:1rem;">최근 활동</h3>
                            <table style="width:100%; font-size:0.88rem;">
                                <c:forEach var="entry" items="${view.activity.timeline}">
                                    <tr>
                                        <td class="muted" style="width:140px;"><c:out value="${entry.stamp}" /></td>
                                        <td style="width:150px;"><span class="chip chip-teal"><c:out value="${entry.label}" /></span></td>
                                        <td><c:out value="${entry.detail}" default="" /></td>
                                        <td class="muted" style="width:60px; text-align:right;">+${entry.points}</td>
                                    </tr>
                                </c:forEach>
                            </table>
                        </c:if>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>

        <%-- FR-84 성장 잠재력 --%>
        <c:if test="${view.scopeGrowth}">
            <div class="card">
                <h2>성장 잠재력</h2>
                <c:choose>
                    <c:when test="${empty view.growthChart}">
                        <p class="muted" style="margin-top:10px;">아직 스펙 완성도 기록이 쌓이지 않았습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <c:set var="chart" value="${view.growthChart}" />
                        <p class="muted" style="margin-top:10px;">
                            스펙 완성도(100점 만점) — 기간마다 마지막 값을 막대로, 직전 기간 대비 증감을 함께 보여 줍니다 (첫 막대는 그 기간 안의 증감).
                        </p>
                        <p class="growth-summary"><c:out value="${chart.summaryText}" /></p>
                        <%-- 주·월·년 탭 — 라디오 버튼 + CSS만으로 전환 (스크립트 없음) --%>
                        <div class="growth-tabs">
                            <c:forEach var="period" items="${chart.periods}" varStatus="st">
                                <input type="radio" name="growth-period" id="growth-${period.key}" class="growth-radio"
                                       ${st.first ? 'checked' : ''}>
                            </c:forEach>
                            <div class="growth-tab-labels">
                                <c:forEach var="period" items="${chart.periods}">
                                    <label for="growth-${period.key}" class="growth-tab-${period.key}"><c:out value="${period.name}" /></label>
                                </c:forEach>
                            </div>
                            <c:forEach var="period" items="${chart.periods}">
                                <div class="growth-panel growth-panel-${period.key}">
                                    <p class="growth-change ${period.down ? 'down' : 'up'}"><c:out value="${period.changeText}" /></p>
                                    <div class="growth-plot">
                                        <div class="growth-axis">
                                            <span>${chart.axisMax}</span>
                                            <span>${chart.axisMin}</span>
                                        </div>
                                        <div class="growth-bars">
                                            <c:forEach var="bar" items="${period.bars}">
                                                <div class="growth-col">
                                                    <div class="growth-delta ${bar.down ? 'down' : ''}"><c:out value="${bar.deltaText}" /></div>
                                                    <div class="growth-score"><c:out value="${bar.scoreText}" /></div>
                                                    <div class="growth-track">
                                                        <div class="growth-bar" style="height:${bar.heightPercent}%;"></div>
                                                    </div>
                                                    <div class="growth-label"><c:out value="${bar.label}" /></div>
                                                </div>
                                            </c:forEach>
                                        </div>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                        <p class="muted" style="font-size:0.78rem; margin-top:6px;">
                            세로축은 ${chart.axisMin}~${chart.axisMax}점 구간만 확대해 보여 줍니다.
                        </p>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>
    </c:otherwise>
</c:choose>

<style>
    /* 활동 잔디 (2026-10-07) — 주 단위 열을 옆으로 쌓고, 한 열이 월~일 7칸이다 */
    .act-grid { display: flex; gap: 3px; margin-top: 10px; overflow-x: auto; padding-bottom: 4px; }
    .act-week { display: flex; flex-direction: column; gap: 3px; flex-shrink: 0; }
    .act-month { font-size: 0.68rem; color: var(--ink-soft); height: 12px; white-space: nowrap; }
    .act-cell { width: 12px; height: 12px; border-radius: 3px; background: var(--border); display: inline-block; }
    .act-cell.lv1 { background: #cfe3dd; }
    .act-cell.lv2 { background: #9ec9bf; }
    .act-cell.lv3 { background: #5aa493; }
    .act-cell.lv4 { background: var(--teal); }
    .act-cell.act-filler { background: transparent; }
    .act-legend { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 0.78rem; }
    .act-legend .muted { margin: 0 4px; }

    /* 성장 잠재력 (2026-10-08) — 주·월·년 탭. 라디오가 체크된 기간의 패널만 보인다 */
    .growth-summary { margin-top: 6px; font-weight: 600; }
    .growth-radio { position: absolute; opacity: 0; pointer-events: none; }
    .growth-tab-labels { display: inline-flex; margin-top: 12px; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
    /* 공통 label(style.css)의 아래 여백 4px 때문에 선택 색이 테두리 끝까지 안 칠해져서 여기서 0으로 */
    .growth-tab-labels label { display: block; margin: 0; padding: 6px 18px; line-height: 1.4; cursor: pointer;
                               font-size: 0.88rem; color: var(--ink-soft); transition: background 0.15s; }
    .growth-tab-labels label:hover { background: var(--teal-bg); }
    .growth-tab-labels label + label { border-left: 1px solid var(--border); }
    #growth-week:checked ~ .growth-tab-labels .growth-tab-week,
    #growth-month:checked ~ .growth-tab-labels .growth-tab-month,
    #growth-year:checked ~ .growth-tab-labels .growth-tab-year { background: var(--teal); color: #fff; font-weight: 600; }
    .growth-panel { display: none; }
    #growth-week:checked ~ .growth-panel-week,
    #growth-month:checked ~ .growth-panel-month,
    #growth-year:checked ~ .growth-panel-year { display: block; }
    .growth-change { margin-top: 10px; font-weight: 600; color: var(--teal); }
    .growth-change.down, .growth-delta.down { color: var(--danger); }
    .growth-plot { display: flex; gap: 8px; margin-top: 8px; }
    .growth-axis { display: flex; flex-direction: column; justify-content: space-between; font-size: 0.72rem;
                   color: var(--ink-soft); height: 160px; margin-top: 36px; text-align: right; min-width: 22px; }
    .growth-bars { display: flex; gap: 10px; align-items: flex-end; overflow-x: auto; padding-bottom: 4px; flex: 1; }
    .growth-col { text-align: center; flex: 0 0 auto; min-width: 42px; }
    .growth-delta { font-size: 0.72rem; color: var(--teal); height: 16px; }
    .growth-score { font-size: 0.85rem; font-weight: 600; height: 20px; }
    .growth-track { height: 160px; display: flex; align-items: flex-end; justify-content: center;
                    border-bottom: 1px solid var(--border); }
    .growth-bar { width: 26px; background: var(--teal); border-radius: 4px 4px 0 0; }
    .growth-col:last-child .growth-bar { background: #155c51; }
    .growth-label { font-size: 0.74rem; color: var(--ink-soft); margin-top: 4px; white-space: nowrap; }
</style>
<jsp:include page="/WEB-INF/views/common/footer.jsp" />
