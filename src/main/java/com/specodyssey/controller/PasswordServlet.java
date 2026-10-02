package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.UserService;
import com.specodyssey.util.LoginThrottle;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 비밀번호 변경 — 현재 비밀번호를 다시 확인한 뒤 새 비밀번호로 바꾼다.
 * 지원자는 "/profile/password", 면접관은 "/interviewer/password"로 오고(RoleFilter가 각자 자기 경로만 열어 준다),
 * 처리 뒤 각자의 프로필 화면으로 돌아간다. 결과 문구는 세션에 한 번만 실어 프로필 화면이 보여 준다.
 * 현재 비밀번호를 연달아 틀리면 로그인과 같은 방식으로 잠시 막는다(남이 열어 둔 세션에서 비밀번호를 알아내는 시도 방지).
 */
@WebServlet({"/profile/password", "/interviewer/password"})
public class PasswordServlet extends HttpServlet {

    static final String MESSAGE_KEY = "passwordMessage";
    static final String ERROR_KEY = "passwordError";
    private static final LoginThrottle THROTTLE = new LoginThrottle();

    private final UserService userService = new UserService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        boolean interviewer = req.getServletPath().startsWith("/interviewer/");
        String back = req.getContextPath() + (interviewer ? "/interviewer/profile" : "/profile");

        String current = req.getParameter("currentPassword");
        String next = req.getParameter("newPassword");
        String confirm = req.getParameter("newPasswordConfirm");
        String throttleKey = "pw:" + loginUser.getId();
        long now = System.currentTimeMillis();

        long locked = THROTTLE.secondsLocked(throttleKey, now);
        if (locked > 0) {
            fail(session, resp, back, "비밀번호를 여러 번 틀려 잠시 막혔습니다. " + ((locked + 59) / 60) + "분 뒤에 다시 시도해주세요.");
            return;
        }
        if (next == null || !next.equals(confirm)) {
            fail(session, resp, back, "새 비밀번호와 확인 입력이 서로 다릅니다.");
            return;
        }

        try {
            userService.changePassword(loginUser.getId(), current, next);
        } catch (UserService.InvalidCredentialException e) {
            THROTTLE.recordFailure(throttleKey, now);
            fail(session, resp, back, e.getMessage());
            return;
        } catch (UserService.InvalidInputException e) {
            fail(session, resp, back, e.getMessage());
            return;
        } catch (SQLException e) {
            throw new ServletException("비밀번호 변경 중 오류가 발생했습니다.", e);
        }
        THROTTLE.recordSuccess(throttleKey);
        req.changeSessionId(); // 비밀번호를 바꾼 시점에 세션 ID도 새로 받는다
        session.setAttribute(MESSAGE_KEY, "비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 사용하세요.");
        resp.sendRedirect(back);
    }

    private static void fail(HttpSession session, HttpServletResponse resp, String back, String message)
            throws IOException {
        session.setAttribute(ERROR_KEY, message);
        resp.sendRedirect(back);
    }
}
