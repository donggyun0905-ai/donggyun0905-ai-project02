package com.specodyssey.controller;

import com.specodyssey.dao.LevelTierDao;
import com.specodyssey.dao.SimulationDao;
import com.specodyssey.dto.UserDto;
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
 * 오른쪽 위 시뮬레이션 패널(common/header.jsp)을 그릴지 정한다 — 로그인한 사람이 테스트 계정(USERS.is_test)이면
 * 요청 속성 simTester = true. 면접관 계정은 미션·로드맵이 없어서 제외한다.
 * 화면에 안 보이게 하는 것일 뿐이고, 실제 권한은 SimulationServlet이 요청마다 다시 확인한다.
 */
@WebFilter(urlPatterns = {"/*"})
public class SimulationPanelFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SimulationPanelFilter.class.getName());
    private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/img/", "/image/"};

    private final SimulationDao simulationDao = new SimulationDao();
    private final LevelTierDao levelTierDao = new LevelTierDao();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && !RoleFilter.INTERVIEWER.equals(user.getUserType())
                && !isStaticOrApi(req.getServletPath())) {
            try {
                if (simulationDao.isTestAccount(user.getId())) {
                    req.setAttribute("simTester", true);
                    req.setAttribute("simTiers", levelTierDao.findAll()); // 목표 점수 "등급 바로가기" (최소 점수 0인 첫 등급은 화면에서 뺀다)
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "테스트 계정 확인 실패 — 시뮬레이션 패널 없이 표시합니다", e);
            }
        }
        chain.doFilter(request, response);
    }

    // 정적 파일과 패널 자신의 상태 확인 요청(1~2초마다)에서는 조회하지 않는다
    private boolean isStaticOrApi(String path) {
        if ("/simulation".equals(path)) {
            return true;
        }
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
