package com.specodyssey.controller;

import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 회원가입.
 * 관련 요구사항: FR-11 · 12 · 13 · 14
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String loginId = req.getParameter("loginId");
        String password = req.getParameter("password");
        String email = req.getParameter("email");
        String name = req.getParameter("name");

        try {
            // 동의는 화면의 체크박스를 우회해 직접 요청해도 받아야 한다 — 서버에서도 막는다.
            boolean interviewer = RoleFilter.INTERVIEWER.equals(req.getParameter("userType"));
            if (!"Y".equals(req.getParameter("privacyConsent"))) {
                throw new IllegalArgumentException("개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
            }
            if (!interviewer && !"Y".equals(req.getParameter("visibilityNotice"))) {
                throw new IllegalArgumentException("면접관에게 보이는 자료 안내를 확인하고 체크해 주세요.");
            }
            Long newUserId;
            if (RoleFilter.INTERVIEWER.equals(req.getParameter("userType"))) {
                newUserId = userService.registerInterviewer(loginId, password, email,
                        PersonalInfo.nameOnly(name), req.getParameter("companyName"));
            } else {
                PersonalInfo personalInfo = PersonalInfo.of(name, req.getParameter("age"),
                        req.getParameter("careerStatus"), req.getParameter("grade"));
                newUserId = userService.register(loginId, password, email, personalInfo,
                        req.getParameter("major"), req.getParameter("interestField"));
            }
            // 비밀번호를 잊었을 때 쓸 복구 코드 — 원문은 서버에 남지 않으니 지금 한 번만 보여 준다
            RecoveryCodeNotice.put(req.getSession(), userService.issueRecoveryCode(newUserId),
                    RecoveryCodeNotice.Context.REGISTER);
            resp.sendRedirect(req.getContextPath() + "/recovery-code");
        } catch (UserService.DuplicateLoginIdException | UserService.InvalidInputException
                 | IllegalArgumentException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/views/signup.jsp").forward(req, resp);
        } catch (SQLException e) {
            throw new ServletException("회원가입 처리 중 오류가 발생했습니다.", e);
        }
    }
}
