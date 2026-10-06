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
import java.time.format.DateTimeFormatter;

/**
 * 로그인.
 * 관련 요구사항: FR-12
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final LoginThrottle THROTTLE = new LoginThrottle();
    // 비밀번호를 확인한 탈퇴 유예 계정 — 탈퇴 취소 버튼을 누를 때까지만 세션(로그인 전)에 잠깐 들고 있는다
    private static final String PENDING_WITHDRAWAL_USER_ID = "pendingWithdrawalUserId";
    private static final String PENDING_WITHDRAWAL_CHECKED_AT = "pendingWithdrawalCheckedAt";
    private static final long PENDING_WITHDRAWAL_TTL_MILLIS = 10 * 60 * 1000L;
    private static final DateTimeFormatter PURGE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

        if ("cancelWithdrawal".equals(req.getParameter("action"))) {
            cancelWithdrawal(req, resp);
            return;
        }

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
            completeLogin(req, resp, user);
        } catch (UserService.PendingWithdrawalException e) {
            // 비밀번호는 맞았다 — 탈퇴를 취소할지 묻는다. 이 단계에서는 아직 로그인시키지 않는다.
            THROTTLE.recordSuccess(throttleKey);
            HttpSession session = req.getSession();
            session.setAttribute(PENDING_WITHDRAWAL_USER_ID, e.getUserId());
            session.setAttribute(PENDING_WITHDRAWAL_CHECKED_AT, System.currentTimeMillis());
            req.setAttribute("purgeDate", e.getPurgeAt().format(PURGE_DATE_FORMAT));
            req.getRequestDispatcher("/WEB-INF/views/withdrawal-cancel.jsp").forward(req, resp);
        } catch (UserService.InvalidCredentialException e) {
            THROTTLE.recordFailure(throttleKey, now);
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("로그인 처리 중 오류가 발생했습니다.", e);
        }
    }

    // 탈퇴 취소 — 방금 이 세션에서 비밀번호를 확인한 계정만 되살린다(아이디·비밀번호를 다시 받지 않는다).
    private void cancelWithdrawal(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(PENDING_WITHDRAWAL_USER_ID);
        Long checkedAt = session == null ? null : (Long) session.getAttribute(PENDING_WITHDRAWAL_CHECKED_AT);
        if (session != null) {
            session.removeAttribute(PENDING_WITHDRAWAL_USER_ID);
            session.removeAttribute(PENDING_WITHDRAWAL_CHECKED_AT);
        }
        if (userId == null || checkedAt == null
                || System.currentTimeMillis() - checkedAt > PENDING_WITHDRAWAL_TTL_MILLIS) {
            req.setAttribute("errorMessage", "시간이 지나 다시 로그인해야 합니다.");
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
            return;
        }
        try {
            completeLogin(req, resp, userService.cancelWithdrawal(userId));
        } catch (UserService.InvalidCredentialException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("탈퇴 취소 처리 중 오류가 발생했습니다.", e);
        }
    }

    private void completeLogin(HttpServletRequest req, HttpServletResponse resp, UserDto user) throws IOException {
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
    }

    // 로그인 직후와 "이미 로그인한 상태로 /login에 온" 경우가 같은 곳으로 가게 한 곳에 둔다.
    private static String homeFor(UserDto user) {
        if (com.specodyssey.util.AdminAccess.isAdmin(user)) {
            return "/admin"; // 관리자는 로그인하면 바로 관리자 화면
        }
        return RoleFilter.INTERVIEWER.equals(user.getUserType()) ? RoleFilter.INTERVIEWER_HOME : "/roadmap";
    }
}
