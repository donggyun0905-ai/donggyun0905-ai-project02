<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="자소서·이력서 첨삭 - 스펙 오디세이" scope="request" />
<c:set var="mainWide" value="true" scope="request" />
<jsp:include page="/WEB-INF/views/common/header.jsp" />

<h1>자소서·이력서 첨삭</h1>
<p class="muted">쓴 글을 붙여넣으면 AI가 목표 직무에 맞춰 고칠 곳을 알려줍니다. 제안은 참고용이고, 최종 문장은 직접 정하세요.</p>

<div class="two-col" style="margin-top:16px;">
    <div class="primary">
        <div class="card">
            <h2>글 입력</h2>
            <div class="row" style="margin-top:10px;">
                <span style="flex:1;"><label>목표 직무</label><input type="text" value="백엔드 개발자"></span>
                <span style="flex:1;"><label>글 종류</label><input type="text" value="자기소개서"></span>
            </div>
            <p><label>내용</label></p>
            <textarea style="min-height:9rem;">저는 백엔드 개발자가 되기 위해 Spring Boot와 MySQL로 REST API 서버를 만들어 보았습니다. 이 프로젝트에서 여러 가지를 배웠고 Docker로 배포까지 해보면서 많은 성장을 했습니다. 앞으로 귀사에서 열심히 하겠습니다.</textarea>
            <button style="margin-top:12px;">AI 첨삭 받기</button>
        </div>

        <div class="card">
            <div class="spread"><h2>첨삭 결과</h2><span class="pill">AI 제안 · 참고용</span></div>
            <p style="margin-top:10px;">경험의 방향은 좋지만 "무엇을 했고 어떻게 해결했는지"가 빠져 있습니다. 추상적인 표현 세 곳을 구체적인 행동으로 바꿔보세요.</p>

            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:12px;">
                <p style="margin:0;"><strong style="color:var(--danger);">원문</strong> "이 프로젝트에서 여러 가지를 배웠고"</p>
                <p style="margin:6px 0;"><strong style="color:var(--teal);">제안</strong> "API 응답 형식을 통일하려고 공통 예외 처리 구조를 직접 설계했습니다"</p>
                <p class="muted" style="margin:0; font-size:0.84rem;">배운 것을 실제로 한 행동으로 바꾸면 경험이 분명해집니다.</p>
            </div>
            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:12px;">
                <p style="margin:0;"><strong style="color:var(--danger);">원문</strong> "많은 성장을 했습니다"</p>
                <p style="margin:6px 0;"><strong style="color:var(--teal);">제안</strong> "배포 중 [겪은 문제]를 [해결 방법]으로 해결하며 [배운 점]을 얻었습니다"</p>
                <p class="muted" style="margin:0; font-size:0.84rem;">대괄호 자리를 실제 경험으로 채우세요.</p>
            </div>
            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:12px;">
                <p style="margin:0;"><strong style="color:var(--danger);">원문</strong> "앞으로 귀사에서 열심히 하겠습니다"</p>
                <p style="margin:6px 0;"><strong style="color:var(--teal);">제안</strong> "입사 후 [지원 회사 서비스]의 [기술 과제]에 제 경험을 보태고 싶습니다"</p>
                <p class="muted" style="margin:0; font-size:0.84rem;">다짐 대신 지원 직무와 이어지는 구체적인 계획을 쓰세요.</p>
            </div>

            <div style="border-top:1px solid var(--border); margin-top:14px; padding-top:12px;">
                <p style="margin:0 0 8px;"><strong>함께 쓰면 좋은 최근 키워드</strong> <span class="pill">예시적 추정</span></p>
                <span class="chip chip-teal">테스트 코드</span>
                <span class="chip chip-teal">클라우드 배포</span>
                <span class="chip chip-teal">컨테이너 운영</span>
            </div>
        </div>
    </div>
    <div class="side">
        <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" />
    </div>
</div>

<jsp:include page="/WEB-INF/views/common/footer.jsp" />
