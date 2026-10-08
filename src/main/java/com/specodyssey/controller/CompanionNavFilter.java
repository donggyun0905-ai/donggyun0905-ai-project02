package com.specodyssey.controller;

import com.specodyssey.dao.CompanionDeviceDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.companion.CompanionAuthService;
import com.specodyssey.service.companion.CompanionReleaseService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 메뉴 "성장 도구" 맨 위 "오셍이들"(데스크톱 캐릭터) 항목에 NEW 꼬리표를 달지 정한다
 * (common/companion-nav.jspf) — 요청 속성 {@code companionNavState}.
 *
 *   "new"   올라간 설치 파일이 있고 아직 연결된 PC가 없다 — 눈에 띄게 NEW
 *   null    그 밖 (항목 자체는 늘 보인다)
 *
 * 하는 일이 하나 더 있다: 이 세션이 "캐릭터 연결"을 눌렀고 캐릭터가 연결을 마쳤으면, 다음 화면으로 넘어갈 때
 * 이 브라우저에 "이 PC의 캐릭터" 쿠키를 남긴다 (CompanionServlet.applyPendingLink — 기다리는 게 없으면 DB를 안 본다).
 *
 * 설치 파일이 있는지는 1분씩 기억해 요청마다 DB를 보지 않는다.
 */
@WebFilter(urlPatterns = {"/*"})
public class CompanionNavFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CompanionNavFilter.class.getName());
    private static final String[] SKIP_PREFIXES = {"/css/", "/js/", "/img/", "/image/", "/api/", "/companion/", "/simulation"};
    private static final long RELEASE_CHECK_MS = 60_000;

    private final CompanionDeviceDao deviceDao = new CompanionDeviceDao();
    private final CompanionReleaseService releaseService = new CompanionReleaseService();
    private final CompanionAuthService authService = new CompanionAuthService();
    private volatile boolean releaseExists;
    private volatile long releaseCheckedAt;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && CompanionLinkFilter.usesCharacter(user) && !skip(req.getServletPath())) {
            try {
                CompanionServlet.applyPendingLink(req, (HttpServletResponse) response, authService);
                if (hasRelease() && deviceDao.findConnectedByUserId(user.getId()).isEmpty()) {
                    req.setAttribute("companionNavState", "new");
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "캐릭터 메뉴 확인 실패 — 꼬리표 없이 표시합니다", e);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean hasRelease() throws java.sql.SQLException {
        long now = System.currentTimeMillis();
        if (now - releaseCheckedAt > RELEASE_CHECK_MS) {
            releaseExists = releaseService.latest() != null;
            releaseCheckedAt = now;
        }
        return releaseExists;
    }

    private static boolean skip(String path) {
        // "/companion/*" 서블릿의 servletPath는 "/companion"이라 "/companion/"으로는 안 걸린다 — 끝에 "/"를 붙여 비교
        String withSlash = path + "/";
        for (String p : SKIP_PREFIXES) {
            if (path.startsWith(p) || withSlash.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
