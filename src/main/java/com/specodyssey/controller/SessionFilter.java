package com.specodyssey.controller;

import com.specodyssey.dto.LevelTierDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserScoreSummaryDto;
import com.specodyssey.service.ScoreService;
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
import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 로그인 세션 확인 필터. 세션에 loginUser가 없으면 로그인 화면으로 보낸다.
 * 다른 사용자 id를 파라미터로 받아 조회하지 않고, 항상 세션의 본인 정보만 쓰는 전제가 여기서 시작된다.
 *
 * 모든 경로({@code /*})에 걸어두고 PUBLIC_PATHS/PUBLIC_PREFIXES에 나열한 것만 예외로 공개한다
 * (화이트리스트 방식). "보호할 경로를 나열"하는 방식은 새 URL(/roadmap, /analysis, /mission 등)을
 * 추가할 때 필터 등록을 깜빡하면 그대로 로그인 없이 열리므로, 기본값이 "보호됨"인 이 방식이 더 안전하다.
 */
@WebFilter(urlPatterns = {"/*"})
public class SessionFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(SessionFilter.class.getName());

    // 로그인 없이 접근 가능한 정확한 경로
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.jsp", "/login", "/register", "/password-reset", "/recovery-code"
    );

    // 로그인 없이 접근 가능한 경로 접두사 (정적 리소스 등)
    // "/share/"는 FR-85 면접관 공유 링크용으로 미리 공개해둔다 — 면접관은 계정이 없어 로그인할 수 없고
    // (FR-14), 접근 제어는 로그인이 아니라 ShareLinkDao.findByToken의 토큰·활성·만료 확인이 대신한다.
    // "/image/"(로고·등급 로고 등)는 사용자별 데이터가 아니라 공용 정적 자산이라 css/js와 같이 공개한다
    // — 없으면 로그인 전 화면(로그인·회원가입)에서 헤더 로고가 못 뜬다.
    // "/api/companion/"은 데스크톱 캐릭터(exe)용 — 쿠키 세션 대신 CompanionApiServlet이 캐릭터 전용 토큰으로 사용자를
    // 확인한다(토큰은 웹 로그인 사용자가 받은 일회용 코드로만 발급). docs/desktop-companion-plan.md 3·6절.
    private static final String[] PUBLIC_PREFIXES = {
            "/css/", "/js/", "/img/", "/image/", "/share/", "/api/companion/"
    };

    private final ScoreService scoreService = new ScoreService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // "/share/*"처럼 와일드카드로 매핑된 서블릿은 getServletPath()가 "/share"까지만 돌려주고 나머지는
        // getPathInfo()에 들어간다. 둘을 이어야 "/share/토큰"이 "/share/" 접두사와 맞는다.
        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        UserDto loginUser = session == null ? null : (UserDto) session.getAttribute("loginUser");
        if (loginUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        attachTierInfo(req, loginUser.getId());
        req.setAttribute("isAdmin", com.specodyssey.util.AdminAccess.isAdmin(loginUser));
        chain.doFilter(request, response);
    }

    // header.jsp 등 공통 화면에서 현재 등급·로고·누적 점수를 바로 쓸 수 있게 요청 속성으로 얹어준다.
    // 적립 이력이 없는 사용자(요약행 없음)는 0점 기준 등급(비기너)으로 보여준다.
    // 실패해도 화면 렌더링 자체를 막을 정도는 아니므로 로그만 남기고 넘어간다.
    private void attachTierInfo(HttpServletRequest req, Long userId) {
        try {
            UserScoreSummaryDto summary = scoreService.getSummary(userId);
            int totalScore = summary == null ? 0 : summary.getTotalScore();
            LevelTierDto tier = scoreService.getTierForScore(totalScore);
            req.setAttribute("totalScore", totalScore);
            req.setAttribute("currentTier", tier);
            req.setAttribute("tierLogoPath", tier == null ? null : scoreService.getTierLogoPath(tier.getId()));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "등급/점수 정보 조회 실패 (userId=" + userId + ")", e);
        }
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
