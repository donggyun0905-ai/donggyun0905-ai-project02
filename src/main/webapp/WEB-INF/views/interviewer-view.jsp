<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
                                                                        <c:when test="${empty doc.documentId}">
                                                                            <p style="margin:4px 0;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span></p>
                                                                        </c:when>
                                                                        <c:when test="${not empty doc.previewType}">
                                                                            <details style="margin:4px 0;">
                                                                                <summary style="cursor:pointer;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span> <c:out value="${doc.fileName}" /> 미리보기</summary>
                                                                                <c:choose>
                                                                                    <c:when test="${doc.previewType == 'pdf'}">
                                                                                        <iframe src="${docUrl}" title="<c:out value='${doc.label}' />" loading="lazy"
                                                                                                style="display:block; width:100%; height:70vh; min-height:480px; margin-top:6px; border:1px solid var(--border); border-radius:var(--radius);"></iframe>
                                                                                    </c:when>
                                                                                    <c:otherwise>
                                                                                        <img src="${docUrl}" alt="<c:out value='${doc.label}' />" loading="lazy"
                                                                                             style="display:block; max-width:100%; margin-top:6px; border:1px solid var(--border); border-radius:var(--radius);">
                                                                                    </c:otherwise>
                                                                                </c:choose>
                                                                                <a href="${docUrl}" target="_blank" rel="noopener" style="font-size:0.84rem;">새 창에서 보기</a>
                                                                            </details>
                                                                        </c:when>
                                                                        <c:otherwise>
                                                                            <p style="margin:4px 0;"><span class="chip chip-teal"><c:out value="${doc.label}" /></span>
                                                                                <a href="${docUrl}" class="file-link"><span class="ic ic-download" aria-hidden="true"></span> <c:out value="${doc.fileName}" /> 내려받기</a></p>
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

        <%-- FR-84 성장 잠재력 --%>
        <c:if test="${view.scopeGrowth}">
            <div class="card">
                <h2>성장 잠재력</h2>
                <c:choose>
                    <c:when test="${empty view.growth}">
                        <p class="muted" style="margin-top:10px;">아직 스펙 완성도 기록이 쌓이지 않았습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <p class="muted" style="margin-top:10px;">스펙 완성도(100점 만점)가 시간에 따라 변한 기록입니다.</p>
                        <div class="row" style="margin-top:10px; align-items:flex-end; gap:20px;">
                            <c:forEach var="point" items="${view.growth}">
                                <div style="text-align:center;">
                                    <div>${point.completenessScore}</div>
                                    <div style="width:24px; height:${point.completenessScore * 0.8}px; background:var(--teal); margin:4px auto 0;"></div>
                                    <div class="muted" style="font-size:0.78rem;">${point.snapshotDate}</div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </c:if>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
