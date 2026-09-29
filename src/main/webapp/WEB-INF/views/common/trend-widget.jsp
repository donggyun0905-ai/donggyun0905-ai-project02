<%-- 화면설계 PDF 공통 위젯 "오늘의 트렌드 기술" — FR-54·55 (2주차 이후 실제 데이터로 교체 예정, 지금은 화면만).
     쓰는 쪽에서 <jsp:include page="/WEB-INF/views/common/trend-widget.jsp" /> 로 오른쪽 칼럼에 넣는다. --%>
<div class="card">
    <h2 style="font-size:1rem;">오늘의 트렌드 기술</h2>
    <p class="muted" style="margin-top:4px;">관심 분야와 관련된 기술만 골라 보여줍니다</p>
    <div style="margin-top:14px;">
        <div style="font-weight:700; font-size:0.92rem;">Virtual Threads</div>
        <div class="muted" style="margin:4px 0;">Java 21의 가상 스레드로 많은 동시 요청을 적은 자원으로 처리하는 방식입니다.</div>
        <a href="#" style="font-size:0.82rem;">출처 보기</a>
    </div>
    <hr style="border:none; border-top:1px solid var(--border); margin:14px 0;">
    <div>
        <div style="font-weight:700; font-size:0.92rem;">Kubernetes</div>
        <div class="muted" style="margin:4px 0;">여러 컨테이너를 묶어 배포와 확장을 자동으로 관리하는 도구입니다.</div>
        <a href="#" style="font-size:0.82rem;">출처 보기</a>
    </div>
    <hr style="border:none; border-top:1px solid var(--border); margin:14px 0;">
    <div>
        <div style="font-weight:700; font-size:0.92rem;">Redis</div>
        <div class="muted" style="margin:4px 0;">자주 읽는 데이터를 메모리에 두어 응답 속도를 높이는 저장소입니다.</div>
        <a href="#" style="font-size:0.82rem;">출처 보기</a>
    </div>
</div>
