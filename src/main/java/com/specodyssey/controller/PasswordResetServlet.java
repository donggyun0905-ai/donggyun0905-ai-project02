package com.specodyssey.controller;

import com.specodyssey.service.UserService;
import com.specodyssey.util.LoginThrottle;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 비밀번호 찾기("/password-reset", 로그인 전이라 공개 경로) — 아이디 + 복구 코드 + 새 비밀번호.
 * 메일 발송 수단이 없어도 되는 방식이다. 맞으면 비밀번호를 바꾸고 쓴 복구 코드는 없애고 새 코드를 한 번 보여 준다.
 * 같은 (아이디, IP)에서 연속으로 틀리면 로그인과 같은 방식으로 잠시 막는다 — 복구 코드를 무작위로 대입하는 시도 방지.
 */
@WebServlet("/password-reset")
public class PasswordResetServlet extends HttpServlet {

    private static final LoginThrottle THROTTLE = new LoginThrottle();

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/password-reset.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String loginId = req.getParameter("loginId");
        String code = req.getParameter("recoveryCode");
        String next = req.getParameter("newPassword");
        String confirm = req.getParameter("newPasswordConfirm");
        String key = "reset|" + LoginThrottle.key(loginId, req.getRemoteAddr());
        long now = System.currentTimeMillis();

        long locked = THROTTLE.secondsLocked(key, now);
        if (locked > 0) {
            showError(req, resp, "시도가 너무 많아 잠시 막혔습니다. " + ((locked + 59) / 60) + "분 뒤에 다시 시도해주세요.");
            return;
        }
        if (next == null || !next.equals(confirm)) {
            showError(req, resp, "새 비밀번호와 확인 입력이 서로 다릅니다.");
            return;
        }
        try {
            String newCode = userService.resetPasswordWithRecoveryCode(loginId, code, next);
            THROTTLE.recordSuccess(key);
            RecoveryCodeNotice.put(req.getSession(), newCode, RecoveryCodeNotice.Context.RESET);
            resp.sendRedirect(req.getContextPath() + "/recovery-code");
        } catch (UserService.InvalidCredentialException e) {
            THROTTLE.recordFailure(key, now);
            showError(req, resp, e.getMessage());
        } catch (UserService.InvalidInputException e) {
            showError(req, resp, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("비밀번호 찾기 처리 중 오류가 발생했습니다.", e);
        }
    }

    private void showError(HttpServletRequest req, HttpServletResponse resp, String message)
            throws ServletException, IOException {
        req.setAttribute("errorMessage", message);
        req.getRequestDispatcher("/WEB-INF/views/password-reset.jsp").forward(req, resp);
    }
}
