<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- 메인 소개(랜딩) 화면. 공통 header.jsp/style.css는 로그인 후 화면용 레이아웃(그리드·드로어)이라
     여기서는 쓰지 않고 landing.css / landing.js만 쓰는 독립 화면으로 둔다. --%>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="loggedIn" value="${not empty sessionScope.loginUser}" />
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>스펙 오디세이 — 목표 직무까지, 다음에 뭘 할지 알려주는 여정</title>
    <meta name="description" content="IT 취업 준비생의 스펙을 진단하고 목표 직무까지 가는 순서 있는 로드맵을 제시합니다.">
    <link rel="stylesheet" href="${ctx}/css/icons.css">
    <link rel="stylesheet" href="${ctx}/css/landing.css">
</head>
<body>
<div class="lp-progress" id="lpProgress"></div>

<header class="lp-nav" id="lpNav">
    <a class="lp-brand" href="${ctx}/"><img src="${ctx}/image/logo.png" alt="">스펙 오디세이</a>
    <nav class="lp-links">
        <a href="#journey">여정</a>
        <a href="#features">기능</a>
        <a href="#tiers">등급</a>
        <a href="#interviewer">면접관</a>
        <a href="#faq">FAQ</a>
    </nav>
    <div class="lp-nav-cta">
        <c:choose>
            <c:when test="${loggedIn}">
                <a class="lp-btn gold" href="${ctx}/dashboard">내 대시보드</a>
            </c:when>
            <c:otherwise>
                <a class="lp-btn ghost" href="${ctx}/login">로그인</a>
                <a class="lp-btn gold" href="${ctx}/register">시작하기</a>
            </c:otherwise>
        </c:choose>
    </div>
</header>

<%-- ============================================================ 히어로 --%>
<section class="lp-hero">
    <canvas id="lpStars" aria-hidden="true"></canvas>
    <div class="lp-hero-inner">
        <p class="lp-eyebrow"><span class="ic ic-compass" aria-hidden="true"></span> IT 취업 준비생을 위한 스펙 항해 지도</p>
        <h1>
            목표는 <span class="lp-type" id="lpType" data-words="백엔드 개발자,프론트엔드 개발자,데이터 엔지니어,DevOps 엔지니어,보안 엔지니어,PM">백엔드 개발자</span><span class="lp-caret" aria-hidden="true"></span><br>
            <em>다음에 뭘 할지</em> 알려드립니다
        </h1>
        <p class="lp-sub">
            스펙을 진단하는 데서 끝나지 않습니다. 목표 직무와의 격차를 계산하고,
            순서가 있는 로드맵과 오늘의 미션으로 매일 한 걸음씩 걷게 합니다.
        </p>
        <div class="lp-hero-cta">
            <c:choose>
                <c:when test="${loggedIn}">
                    <a class="lp-btn gold big" href="${ctx}/dashboard">항해 이어가기 →</a>
                </c:when>
                <c:otherwise>
                    <a class="lp-btn gold big" href="${ctx}/register">내 여정 시작하기 →</a>
                    <a class="lp-btn ghost big" href="${ctx}/login">로그인</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

    <%-- 예시 화면 목업 — 실제 사용자 데이터가 아니라 정적인 예시다 --%>
    <div class="lp-mock-wrap" id="lpTilt" aria-hidden="true">
        <div class="lp-mock">
            <div class="lp-mock-bar"><i></i><i></i><i></i><span>예시 화면 · 격차 분석 — 백엔드 개발자</span></div>
            <div class="lp-mock-body">
                <div class="lp-mock-col">
                    <div class="lp-mock-title">직무 적합도</div>
                    <div class="lp-ring" style="--p:72"><b><span class="lp-count" data-to="72">0</span>%</b></div>
                    <div class="lp-chip-row"><span class="lp-chip ok">Java</span><span class="lp-chip ok">MySQL</span><span class="lp-chip warn">Docker</span></div>
                </div>
                <div class="lp-mock-col grow">
                    <div class="lp-mock-title">기술별 격차</div>
                    <div class="lp-bar"><label>Java</label><div><i style="--w:90%"></i></div></div>
                    <div class="lp-bar"><label>Spring</label><div><i style="--w:64%"></i></div></div>
                    <div class="lp-bar"><label>SQL</label><div><i style="--w:78%"></i></div></div>
                    <div class="lp-bar low"><label>Docker</label><div><i style="--w:28%"></i></div></div>
                    <div class="lp-bar low"><label>AWS</label><div><i style="--w:18%"></i></div></div>
                </div>
                <div class="lp-mock-col">
                    <div class="lp-mock-title">다음 할 일</div>
                    <ul class="lp-todo">
                        <li class="done">SQL 조인 복습</li>
                        <li class="now">Docker 기초</li>
                        <li>AWS 배포 실습</li>
                        <li>포트폴리오 정리</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</section>

<%-- ============================================================ 기술 마키 --%>
<section class="lp-marquee" aria-hidden="true">
    <div class="lp-track">
        <c:forEach begin="1" end="2">
            <span>Java</span><span>Spring</span><span>MySQL</span><span>React</span><span>TypeScript</span><span>Python</span>
            <span>Docker</span><span>Kubernetes</span><span>AWS</span><span>Git</span><span>Linux</span><span>정보처리기사</span><span>SQLD</span>
        </c:forEach>
    </div>
    <div class="lp-track reverse">
        <c:forEach begin="1" end="2">
            <span>백엔드</span><span>프론트엔드</span><span>데이터</span><span>DevOps</span><span>보안</span><span>PM</span>
            <span>격차 분석</span><span>로드맵</span><span>오늘의 미션</span><span>D-day</span><span>자소서 첨삭</span><span>공유 링크</span>
        </c:forEach>
    </div>
</section>

<%-- ============================================================ 여정 5단계 --%>
<section class="lp-section" id="journey">
    <div class="lp-head reveal">
        <p class="lp-kicker">THE JOURNEY</p>
        <h2>진단에서 끝나지 않는,<br>순서가 있는 여정</h2>
        <p>가입부터 대시보드까지 다섯 걸음. 한 바퀴 돌면 오늘 할 일이 정해집니다.</p>
    </div>
    <ol class="lp-journey" id="lpJourney">
        <li class="lp-journey-line"><i id="lpJourneyFill"></i></li>
        <li class="lp-step reveal"><span class="lp-step-no">1</span>
            <div><h3>가입</h3><p>지원자 또는 면접관으로 계정을 만듭니다.</p></div></li>
        <li class="lp-step reveal"><span class="lp-step-no">2</span>
            <div><h3>프로필 입력</h3><p>보유 기술, 자격증, 프로젝트, 이력서를 한곳에 정리합니다.</p></div></li>
        <li class="lp-step reveal"><span class="lp-step-no">3</span>
            <div><h3>격차 분석</h3><p>목표 직무가 요구하는 기술과 내 스펙을 비교해 부족한 부분을 짚어냅니다. 직무를 못 정했다면 설문으로 먼저 찾아봅니다.</p></div></li>
        <li class="lp-step reveal"><span class="lp-step-no">4</span>
            <div><h3>로드맵 제시</h3><p>격차를 쉬운 것부터 순서대로 배치한 나만의 학습 경로가 만들어집니다.</p></div></li>
        <li class="lp-step reveal"><span class="lp-step-no">5</span>
            <div><h3>대시보드</h3><p>진행률, 등급, 오늘의 미션, 다가오는 일정을 매일 한 화면에서 확인합니다.</p></div></li>
    </ol>
</section>

<%-- ============================================================ 기능 탭 (자동 전환)
     각 패널의 화면은 실제 화면의 구성(헤더·카드·표·배지)을 본뜬 정적인 예시다 — 실제 사용자 데이터가 아니다. --%>
<section class="lp-section dark" id="features">
    <div class="lp-head reveal">
        <p class="lp-kicker">FEATURES</p>
        <h2>취업 준비에 필요한 것,<br>한 배에 다 실었습니다</h2>
        <p>아래는 실제 서비스 화면을 본뜬 예시입니다.</p>
    </div>
    <div class="lp-tabs reveal" id="lpTabs">
        <div class="lp-tablist" role="tablist">
            <button class="lp-tab is-active" role="tab" type="button"><b>격차 분석</b><small>목표 직무와 내 스펙의 차이를 수치로</small><i></i></button>
            <button class="lp-tab" role="tab" type="button"><b>내 로드맵</b><small>무엇을 어떤 순서로 할지</small><i></i></button>
            <button class="lp-tab" role="tab" type="button"><b>오늘의 미션</b><small>매일 코딩테스트 문제 3개</small><i></i></button>
            <button class="lp-tab" role="tab" type="button"><b>데이터 인사이트</b><small>직무별 기술 수요 흐름</small><i></i></button>
            <button class="lp-tab" role="tab" type="button"><b>자소서 첨삭</b><small>AI가 문장 단위로 피드백</small><i></i></button>
            <button class="lp-tab" role="tab" type="button"><b>D-day 알림</b><small>접수일과 마감일을 놓치지 않게</small><i></i></button>
        </div>
        <div class="lp-panels">
            <div class="lp-panel is-active">
                <h3>부족한 기술이 무엇인지, 한눈에</h3>
                <p>목표 직무가 요구하는 기술과 내 보유 기술을 하나씩 비교해 충족·부족을 보여줍니다. 결과에서 바로 로드맵을 만들 수 있습니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · 격차 분석</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">격차 분석</div>
                            <div class="lp-m-card">
                                <h4>한눈에 보기</h4>
                                <div><b class="lp-m-big">4</b> / 13개 요구 기술 충족</div>
                                <div class="lp-m-prog"><i style="--w:31%"></i></div>
                            </div>
                            <div class="lp-m-card">
                                <h4>충족·부족 목록</h4>
                                <ul class="lp-m-rows lp-stag">
                                    <li>Java<span class="ok"><span class="ic ic-check" aria-hidden="true"></span> 충족</span></li>
                                    <li>SQL<span class="no"><span class="ic ic-x" aria-hidden="true"></span> 부족</span></li>
                                    <li>Spring Boot<span class="no"><span class="ic ic-x" aria-hidden="true"></span> 부족</span></li>
                                    <li>MySQL<span class="ok"><span class="ic ic-check" aria-hidden="true"></span> 충족</span></li>
                                    <li>Docker<span class="no"><span class="ic ic-x" aria-hidden="true"></span> 부족</span></li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="lp-panel">
                <h3>지금 할 일부터, 순서대로</h3>
                <p>격차 분석 결과가 입문·핵심·심화·전문가 단계의 여정이 됩니다. 자격증, 프로젝트, 기술을 지금 할 일부터 차례로 보여줍니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · 내 로드맵</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">내 로드맵</div>
                            <div class="lp-m-chips"><span class="on">입문 1/7</span><span>핵심</span><span>심화</span><span>전문가</span></div>
                            <div class="lp-m-card">
                                <h4>여정 · 지금 할 일 (입문)</h4>
                                <div class="lp-m-path lp-stag">
                                    <div class="l done"><em>자격증</em>직무에서 자주 요구되는 자격증을 취득합니다.<i><span class="ic ic-check" aria-hidden="true"></span></i></div>
                                    <div class="r now"><em>프로젝트</em>부족한 기술을 다뤄볼 프로젝트를 진행합니다.<i></i></div>
                                    <div class="l"><em>기술</em>필수로 요구되는 SQL을 채웁니다.<i></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="lp-panel">
                <h3>하루 세 문제, 끊기지 않는 항해</h3>
                <p>매일 등급에 맞는 난이도의 코딩테스트 문제 3개가 주어집니다. 풀면 점수가 쌓이고 연속 일수가 이어집니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · 오늘의 미션</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">오늘의 미션</div>
                            <div class="lp-m-grid">
                                <div class="lp-m-card">
                                    <h4>연속 4일째</h4>
                                    <div class="lp-m-days"><i>토</i><i>일</i><i class="on">월</i><i class="on">화</i><i class="on">수</i><i class="on">목</i><i class="today">금</i></div>
                                </div>
                                <div class="lp-m-card">
                                    <h4>문제 난이도</h4>
                                    현재 등급에 맞춰 <b>Lv0~1</b> 문제가 나옵니다.
                                </div>
                            </div>
                            <div class="lp-m-card">
                                <h4>오늘의 문제 3개<small>2 / 3 완료</small></h4>
                                <ul class="lp-m-rows lp-stag">
                                    <li>문자열 뒤집기<span class="lp-m-badge done">완료</span></li>
                                    <li>두 수의 합<span class="lp-m-badge done">완료</span></li>
                                    <li>배열 정렬하기<span class="lp-m-badge">남음</span></li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="lp-panel">
                <h3>시장 흐름 속 나의 위치</h3>
                <p>자주 언급되는 기술 순위, 또래 비교, 약점 히트맵을 한 화면에서 봅니다. 무엇부터 배울지 정할 때 근거가 됩니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · 데이터 인사이트</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">데이터 인사이트</div>
                            <div class="lp-m-card">
                                <h4>취업시장 트렌드<span class="lp-m-pill">예시적 추정</span></h4>
                                <div class="lp-m-hbar"><label>Spring Boot</label><div><i style="--w:95%"></i></div></div>
                                <div class="lp-m-hbar"><label>Java</label><div><i style="--w:85%"></i></div></div>
                                <div class="lp-m-hbar"><label>MySQL</label><div><i style="--w:70%"></i></div></div>
                                <div class="lp-m-hbar"><label>Docker</label><div><i style="--w:64%"></i></div></div>
                                <div class="lp-m-hbar"><label>Kubernetes</label><div><i style="--w:52%"></i></div></div>
                            </div>
                            <div class="lp-m-card">
                                <h4>또래 비교<span class="lp-m-pill">샘플 데이터 기준</span></h4>
                                <div class="lp-m-hbar gold"><label>나 · 62</label><div><i style="--w:62%"></i></div></div>
                                <div class="lp-m-hbar grey"><label>평균 · 55</label><div><i style="--w:55%"></i></div></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="lp-panel">
                <h3>막막한 자소서, 어디를 고칠지부터</h3>
                <p>글을 붙여넣으면 AI가 목표 직무에 맞춰 고칠 곳과 이유를 알려줍니다. 제안은 참고용이고 최종 문장은 직접 정합니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · 자소서 첨삭</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">자소서·이력서 첨삭</div>
                            <div class="lp-m-card">
                                <h4>첨삭 결과<span class="lp-m-pill">AI 제안 · 참고용</span></h4>
                                <div class="lp-m-fb lp-stag">
                                    <p>추상적인 표현을 구체적인 행동으로 바꿔보세요.</p>
                                    <p><b class="no">원문</b> "이 프로젝트에서 여러 가지를 배웠고"</p>
                                    <p><b class="ok">제안</b> "공통 예외 처리 구조를 직접 설계했습니다"</p>
                                    <p><b class="no">원문</b> "많은 성장을 했습니다"</p>
                                    <p><b class="ok">제안</b> "배포 중 겪은 문제를 해결하며 배운 점을 얻었습니다"</p>
                                    <p class="lp-m-chips"><span class="on">테스트 코드</span><span class="on">클라우드 배포</span><span class="on">컨테이너 운영</span></p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="lp-panel">
                <h3>접수일도 마감일도 놓치지 않게</h3>
                <p>자격증 접수일과 공채 마감일을 직접 등록해 두면 마감이 가까운 순으로 보여줍니다.</p>
                <div class="lp-screen">
                    <div class="lp-screen-bar"><i></i><i></i><i></i><span>예시 화면 · D-day 알림</span></div>
                    <div class="lp-screen-view">
                        <div class="lp-m-head"><span><span class="ic ic-menu" aria-hidden="true"></span> 목록</span>스펙 오디세이</div>
                        <div class="lp-m-body">
                            <div class="lp-m-title">D-day 알림</div>
                            <div class="lp-m-card lp-m-banner"><b class="no">D-3</b>신입 공채 서류 마감<small>공채</small></div>
                            <div class="lp-m-card">
                                <h4>다가오는 일정</h4>
                                <ul class="lp-m-rows lp-m-boxed lp-stag">
                                    <li><span><b>신입 공채 서류 마감</b><small>공채</small></span><span class="lp-m-badge soon">D-3</span></li>
                                    <li><span><b>자격증 시험</b><small>자격증</small></span><span class="lp-m-badge">D-12</span></li>
                                    <li><span><b>코딩테스트 스터디 발표</b><small>기타</small></span><span class="lp-m-badge">D-21</span></li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

<%-- ============================================================ 숫자 --%>
<section class="lp-stats">
    <div class="reveal"><b><span class="lp-count" data-to="6">0</span>개</b><span>IT 직무군</span></div>
    <div class="reveal"><b><span class="lp-count" data-to="5">0</span>단계</b><span>여정 루프</span></div>
    <div class="reveal"><b><span class="lp-count" data-to="5">0</span>등급</b><span>성장 단계</span></div>
    <div class="reveal"><b>하루 <span class="lp-count" data-to="3">0</span>개</b><span>오늘의 미션</span></div>
</section>

<%-- ============================================================ 등급 --%>
<section class="lp-section" id="tiers">
    <div class="lp-head reveal">
        <p class="lp-kicker">GROW</p>
        <h2>첫걸음에서 오디세이아까지</h2>
        <p>미션을 수행할수록 점수가 쌓이고 등급이 오릅니다.</p>
    </div>
    <div class="lp-tiers">
        <div class="lp-tier reveal"><img src="${ctx}/image/tier-1-beginner.png" alt=""><b>첫걸음</b></div>
        <div class="lp-tier reveal"><img src="${ctx}/image/tier-2-jobseeker.png" alt=""><b>방랑자</b></div>
        <div class="lp-tier reveal"><img src="${ctx}/image/tier-3-practitioner.png" alt=""><b>항해자</b></div>
        <div class="lp-tier reveal"><img src="${ctx}/image/tier-4-almost.png" alt=""><b>개척자</b></div>
        <div class="lp-tier reveal"><img src="${ctx}/image/tier-5-legend.png" alt=""><b>오디세이아</b></div>
    </div>
</section>

<%-- ============================================================ 면접관 --%>
<section class="lp-section alt" id="interviewer">
    <div class="lp-split">
        <div class="reveal">
            <p class="lp-kicker">FOR INTERVIEWERS</p>
            <h2>링크 하나로<br>면접관에게 보여주세요</h2>
            <p>공유 링크를 발급하면 면접관이 내 스펙과 이력서를 읽기 전용으로 볼 수 있습니다. 링크는 언제든 중단하거나 삭제할 수 있습니다.</p>
            <ul class="lp-list">
                <li>추측할 수 없는 랜덤 토큰 링크</li>
                <li>면접관 계정은 공유받은 이력을 모아 보고 지원자를 비교</li>
                <li>수정 권한 없는 읽기 전용 화면</li>
            </ul>
        </div>
        <div class="lp-share reveal" aria-hidden="true">
            <div class="lp-share-url"><span class="ic ic-link" aria-hidden="true"></span> <span>/share/</span><span id="lpToken">••••••••••••</span></div>
            <div class="lp-share-row"><span class="lp-pill on">공개 중</span><span class="lp-pill">읽기 전용</span></div>
            <div class="lp-share-card"><u></u><u class="short"></u><u></u></div>
        </div>
    </div>
</section>

<%-- ============================================================ FAQ --%>
<section class="lp-section" id="faq">
    <div class="lp-head reveal">
        <p class="lp-kicker">FAQ</p>
        <h2>자주 묻는 질문</h2>
    </div>
    <div class="lp-faq reveal">
        <details>
            <summary>어떤 직무를 지원하나요?</summary>
            <p>IT 계열 직무에 한정합니다. 백엔드, 프론트엔드, 데이터, DevOps, 보안, PM 여섯 직무군을 다룹니다.</p>
        </details>
        <details>
            <summary>아직 목표 직무를 정하지 못했어요.</summary>
            <p>직무 찾기 설문으로 나에게 맞는 직무를 먼저 추천받고, 그 결과로 바로 격차 분석을 이어갈 수 있습니다.</p>
        </details>
        <details>
            <summary>다른 진단 서비스와 무엇이 다른가요?</summary>
            <p>부족한 점을 알려주는 데서 멈추지 않고, 무엇을 어떤 순서로 할지 로드맵으로 제시하고 매일의 미션으로 이어 줍니다.</p>
        </details>
        <details>
            <summary>내 정보는 누가 볼 수 있나요?</summary>
            <p>본인만 볼 수 있습니다. 공유 링크를 직접 발급한 경우에만 그 링크를 가진 면접관이 읽기 전용으로 볼 수 있습니다.</p>
        </details>
    </div>
</section>

<%-- ============================================================ 마지막 CTA --%>
<section class="lp-final">
    <div class="reveal">
        <h2>오늘, 첫 닻을 올려 보세요</h2>
        <p>프로필을 입력하면 다음에 할 일이 정해집니다.</p>
        <c:choose>
            <c:when test="${loggedIn}">
                <a class="lp-btn gold big" href="${ctx}/dashboard">내 대시보드로 →</a>
            </c:when>
            <c:otherwise>
                <a class="lp-btn gold big" href="${ctx}/register">내 여정 시작하기 →</a>
            </c:otherwise>
        </c:choose>
    </div>
</section>

<footer class="lp-footer"><span class="ic ic-compass" aria-hidden="true"></span> 스펙 오디세이 — 당신의 취업 항해를 돕습니다</footer>

<script src="${ctx}/js/landing.js"></script>
</body>
</html>
