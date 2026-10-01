package com.specodyssey.controller;

import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.InsightService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 데이터 인사이트 화면. 관련 요구사항: FR-45~48
 * 화면설계 PDF "10. 데이터 인사이트" 기준. 목표 직무가 없으면 직무 찾기 안내만 보여준다.
 */
@WebServlet("/insights")
public class InsightsServlet extends HttpServlet {

    private final InsightService insightService = new InsightService();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
        try {
            // 세션 객체는 프로필 수정 전 값일 수 있어 DB에서 다시 읽는다
            UserDto user = userDao.findById(userId);
            if (user.getDesiredJobId() == null) {
                req.setAttribute("noTargetJob", true);
            } else {
                req.setAttribute("insight", insightService.build(user));
            }
        } catch (SQLException e) {
            throw new ServletException("데이터 인사이트를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/insights.jsp").forward(req, resp);
    }
}
