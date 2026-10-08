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
 * 메뉴 "성장 도구" 맨 위의 [캐릭터 내려받기]를 보일지 정한다 (common/header.jsp) — 요청 속성 showCompanionDownload.
 * 브라우저는 PC에 프로그램이 설치됐는지 알려 주지 않으므로, 이 계정에 연결된 PC가 있으면 설치된 것으로 보고 숨긴다.
 * 올라간 설치 파일이 없을 때도 숨긴다 (이건 1분씩 기억해 요청마다 DB를 보지 않는다).
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
                    req.setAttribute("showCompanionDownload", true);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "캐릭터 내려받기 메뉴 확인 실패 — 메뉴 없이 표시합니다", e);
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
