package com.specodyssey.controller;

import com.specodyssey.dao.CompanionDeviceDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.companion.CompanionReleaseService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 메뉴 "성장 도구" 맨 위에 데스크톱 캐릭터 항목을 무엇으로 보일지 정한다
 * (common/companion-nav.jspf) — 요청 속성 {@code companionNavState}.
 *
 *   null       아무것도 안 보인다. 올라간 설치 파일이 없거나, 이미 연결된 PC가 있어 할 일이 없다.
 *   "download" [캐릭터 내려받기] — 아직 설치 파일을 받아간 적이 없다.
 *   "launch"   [캐릭터 켜기]     — 받아갔으니 프로필의 [캐릭터 켜기]와 같은 일을 한다.
 *
 * 브라우저는 PC에 프로그램이 설치됐는지 알려 주지 않는다. 그래서 두 가지 간접 신호를 쓴다:
 * 연결된 PC가 있으면 설치·연결까지 끝난 것으로 보고 항목을 아예 숨기고, 설치 파일을 받아간 기록
 * (세션 {@link CompanionServlet#DOWNLOADED_ATTR})이 있으면 켜기로 바꾼다. 둘 다 틀릴 수 있어서
 * 켜기를 눌러 안 열리면 프로필 칸으로 보내 "이미 설치했어요 / 내려받기"를 다시 고를 수 있게 한다.
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
    private volatile boolean releaseExists;
    private volatile long releaseCheckedAt;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && !RoleFilter.INTERVIEWER.equals(user.getUserType()) && !skip(req.getServletPath())) {
            try {
                if (hasRelease() && deviceDao.findConnectedByUserId(user.getId()).isEmpty()) {
                    boolean downloaded = Boolean.TRUE.equals(session.getAttribute(CompanionServlet.DOWNLOADED_ATTR));
                    req.setAttribute("companionNavState", downloaded ? "launch" : "download");
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "캐릭터 메뉴 확인 실패 — 메뉴 없이 표시합니다", e);
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
        for (String p : SKIP_PREFIXES) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
