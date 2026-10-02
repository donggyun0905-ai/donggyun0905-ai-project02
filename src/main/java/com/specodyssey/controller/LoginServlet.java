package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.util.LoginThrottle;
import com.specodyssey.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 로그인.
 * 관련 요구사항: FR-12
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final LoginThrottle THROTTLE = new LoginThrottle();

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 이미 로그인한 상태에서 로고("/" → index.jsp → "/login")를 눌렀을 때 로그인 화면이 또 뜨지 않게,
        // 로그인 성공 후와 같은 메인 화면(지원자는 로드맵, 면접관은 공유받은 이력)으로 보낸다.
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("loginUser") instanceof UserDto loggedIn) {
            resp.sendRedirect(req.getContextPath() + homeFor(loggedIn));
            return;
        }
        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String loginId = req.getParameter("loginId");
        String password = req.getParameter("password");

        String throttleKey = LoginThrottle.key(loginId, req.getRemoteAddr());
        long now = System.currentTimeMillis();
        long lockedSeconds = THROTTLE.secondsLocked(throttleKey, now);
        if (lockedSeconds > 0) {
            req.setAttribute("errorMessage", "로그인에 여러 번 실패해 잠시 막혔습니다. "
                    + ((lockedSeconds + 59) / 60) + "분 뒤에 다시 시도해주세요.");
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
            return;
        }

        try {
            UserDto user = userService.login(loginId, password);
            THROTTLE.recordSuccess(throttleKey);
            user.setPasswordHash(null); // 세션에는 해시조차 남기지 않는다
            user.setRecoveryCodeHash(null);
            HttpSession session = req.getSession();
            req.changeSessionId(); // 세션 고정(session fixation) 공격 방지 — 인증 성공 시 세션 ID 교체
            session.setAttribute("loginUser", user);
            // "/"는 index.jsp가 "/login"으로 되돌려보내는 자리라(별도 랜딩 화면 없음),
            // 로그인 성공 후에는 어딘가로 보내야 한다 — 안 그러면 로그인하자마자 다시 로그인
            // 화면으로 튕긴다. 지원자의 메인 화면은 로드맵(사용자 요청, 2026-09-30).
            // 면접관 계정은 대시보드·로드맵이 없다 — 공유받은 이력 화면이 첫 화면이다.
            resp.sendRedirect(req.getContextPath() + homeFor(user));
        } catch (UserService.InvalidCredentialException e) {
            THROTTLE.recordFailure(throttleKey, now);
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("로그인 처리 중 오류가 발생했습니다.", e);
        }
    }

    // 로그인 직후와 "이미 로그인한 상태로 /login에 온" 경우가 같은 곳으로 가게 한 곳에 둔다.
    private static String homeFor(UserDto user) {
        if (com.specodyssey.util.AdminAccess.isAdmin(user)) {
            return "/admin"; // 관리자는 로그인하면 바로 관리자 화면
        }
        return RoleFilter.INTERVIEWER.equals(user.getUserType()) ? RoleFilter.INTERVIEWER_HOME : "/roadmap";
    }
}
