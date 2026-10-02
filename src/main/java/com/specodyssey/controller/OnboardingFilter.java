package com.specodyssey.controller;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dao.UserSurveyAnswerDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.util.AdminAccess;
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

/**
 * 처음 설문을 하기 전의 접근 제한. 지원자(APPLICANT)가 직무 발굴 설문을 아직 안 했고 목표 직무도 없으면
 * 설문("/job-discovery")과 프로필("/profile…")·계정 관련 화면만 쓸 수 있고, 나머지는 설문으로 보낸다.
 * 설문을 한 번이라도 했거나 프로필에서 목표 직무를 정했으면(기존 가입자 포함) 막지 않는다.
 * 면접관과 관리자 계정은 대상이 아니다. 한 번 통과하면 세션에 기억해 매 요청마다 DB를 보지 않는다.
 */
@WebFilter(urlPatterns = {"/*"})
public class OnboardingFilter implements Filter {

    static final String SESSION_KEY = "onboardingDone";
    static final String NOTICE_KEY = "onboardingNotice";
    static final String SURVEY_PATH = "/job-discovery";

    private static final Set<String> ALLOWED_PATHS = Set.of(
            SURVEY_PATH, "/profile", "/logout", "/login", "/register", "/password-reset", "/recovery-code");
    private static final String[] ALLOWED_PREFIXES = {
            "/profile/", "/css/", "/js/", "/img/", "/image/", "/share/"
    };

    private final UserDao userDao = new UserDao();
    private final UserSurveyAnswerDao surveyAnswerDao = new UserSurveyAnswerDao();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user && appliesTo(user) && !Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
            if (!isAllowedBeforeSurvey(path)) {
                try {
                    if (isOnboarded(user.getId())) {
                        session.setAttribute(SESSION_KEY, Boolean.TRUE);
                    } else {
                        session.setAttribute(NOTICE_KEY, "먼저 직무 찾기 설문을 해 주세요. 설문을 마치기 전에는 설문과 내 프로필만 쓸 수 있습니다.");
                        resp.sendRedirect(req.getContextPath() + SURVEY_PATH);
                        return;
                    }
                } catch (SQLException e) {
                    throw new ServletException("설문 여부를 확인하는 중 오류가 발생했습니다.", e);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean appliesTo(UserDto user) {
        return !RoleFilter.INTERVIEWER.equals(user.getUserType()) && !AdminAccess.isAdmin(user);
    }

    static boolean isAllowedBeforeSurvey(String path) {
        if (ALLOWED_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // 설문을 했거나, 프로필·추천 선택으로 목표 직무가 이미 있으면 통과 — 세션의 사용자 정보는 오래됐을 수 있어 DB에서 다시 읽는다
    private boolean isOnboarded(Long userId) throws SQLException {
        if (surveyAnswerDao.hasJobDiscoveryAnswer(userId)) {
            return true;
        }
        UserDto fresh = userDao.findById(userId);
        return fresh != null && fresh.getDesiredJobId() != null;
    }
}
