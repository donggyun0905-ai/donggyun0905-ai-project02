<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
</main>
<c:if test="${sideWidgets}">
<aside class="side-left"><jsp:include page="/WEB-INF/views/common/side-left-widgets.jsp" /></aside>
<aside class="side-right"><jsp:include page="/WEB-INF/views/common/side-right-widgets.jsp" /></aside>
</c:if>
<%-- FR-54 전용 우측 사이드바 자리는 아니고(그건 각 화면이 트렌드 위젯을 본문 오른쪽 칼럼에
     직접 넣는다 — common/trend-widget.jsp), 이 자리는 비워둔 채 유지한다.
     아래 <aside>는 태그 사이에 공백 한 글자도 없어야 한다 — 있으면 #trend-sidebar:empty가
     안 먹혀서(공백도 자식 텍스트 노드로 쳐줘서 "비어있음" 판정 실패) width:260px가 항상
     적용돼 모든 화면에서 본문이 왼쪽으로 밀려 보이는 버그가 났었다(2026-09-29 실사용자 확인). --%><aside id="trend-sidebar"></aside>
<footer class="site-footer"><span class="ic ic-compass" aria-hidden="true"></span> 스펙 오디세이 — 당신의 취업 항해를 돕습니다</footer>
</body>
</html>
