package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * 서비스 흐름 소개. 관련 요구사항: FR-115 온보딩
 *
 * 설문을 아직 하지 않은 지원자는 OnboardingFilter가 직무 찾기로 보내는데, 왜 설문부터 하는지를
 * 아무도 설명해 주지 않아서 "가입하자마자 설문을 요구하는 화면"으로 보였다. 그래서 그 앞에 이 화면을 둔다 —
 * 진단 → 길 제시 → 미션이 어떻게 이어지는지 세 칸으로 보여 주고 설문으로 넘긴다.
 *
 * 한 번 보면 다시 뜨지 않는다. 플래그는 세션에만 둔다 — "본 적 있음"을 USERS에 남기려면 컬럼이 필요한데,
 * 이 안내는 못 보면 손해가 아니라 한 번 더 보는 것뿐이라 스키마를 늘릴 이유가 없다고 봤다.
 * 메뉴에서 언제든 다시 열 수 있게 GET은 플래그와 무관하게 항상 화면을 보여준다.
 */
@WebServlet("/welcome")
public class WelcomeServlet extends HttpServlet {

    static final String SEEN_KEY = "welcomeSeen";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.setAttribute(SEEN_KEY, Boolean.TRUE);
            Object loginUser = session.getAttribute("loginUser");
            if (loginUser instanceof UserDto user) {
                req.setAttribute("userName", user.getName());
            }
        }
        req.getRequestDispatcher("/WEB-INF/views/welcome.jsp").forward(req, resp);
    }

    /** 이 세션에서 아직 안내를 보지 않았는지 — OnboardingFilter가 설문으로 보내기 전에 확인한다. */
    static boolean notSeenYet(HttpSession session) {
        return session != null && !Boolean.TRUE.equals(session.getAttribute(SEEN_KEY));
    }
}
