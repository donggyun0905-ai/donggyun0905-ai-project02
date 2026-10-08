package com.specodyssey.controller;

import com.specodyssey.util.Pager;
import com.specodyssey.dto.DdayItemDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.DdayPlanService;
import com.specodyssey.service.DdayService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * D-day 알림 화면. 관련 요구사항: FR-71 · 72
 * 화면설계 PDF "17. D-day 알림" 기준. 이메일 알림(FR-73)은 아직 없다.
 */
@WebServlet("/dday")
public class DdayServlet extends HttpServlet {

    private static final int PAGE_SIZE = 10;

    private final DdayService ddayService = new DdayService();
    private final DdayPlanService ddayPlanService = new DdayPlanService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            showPage(req, resp, loginUserId(req));
        } catch (SQLException e) {
            throw new ServletException("일정을 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);

        try {
            String action = req.getParameter("action");
            if ("delete".equals(action)) {
                ddayService.deleteItem(userId, Long.valueOf(req.getParameter("alertId")));
            } else {
                String dateParam = req.getParameter("targetDate");
                LocalDate targetDate = (dateParam == null || dateParam.isBlank()) ? null : LocalDate.parse(dateParam);
                boolean update = "update".equals(action);
                try {
                    if (update) {
                        ddayService.updateItem(userId, Long.valueOf(req.getParameter("alertId")),
                                req.getParameter("title"), targetDate, req.getParameter("alertType"));
                    } else {
                        ddayService.addItem(userId, req.getParameter("title"), targetDate,
                                req.getParameter("alertType"), LocalDate.now());
                    }
                } catch (IllegalArgumentException e) {
                    // 수정 실패는 목록 쪽에, 추가 실패는 추가 폼 쪽에 보여준다
                    req.setAttribute(update ? "listErrorMessage" : "errorMessage", e.getMessage());
                    showPage(req, resp, userId);
                    return;
                }
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("일정 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/dday");
    }

    private void showPage(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws SQLException, ServletException, IOException {
        LocalDate today = LocalDate.now();
        List<DdayItemDto> items = ddayService.listItems(userId, today);
        // 가장 급한 일정은 전체에서 고르고, 목록만 쪽으로 자른다
        Pager<DdayItemDto> pager = Pager.of(items, Pager.parsePage(req.getParameter("page")), PAGE_SIZE);
        req.setAttribute("items", pager.getItems());
        req.setAttribute("pager", pager);
        DdayItemDto urgent = ddayService.findMostUrgent(items);
        req.setAttribute("urgentItem", urgent);
        req.setAttribute("today", today);
        // 가장 급한 일정까지 남은 기간에 무엇부터 하면 점수가 가장 많이 오르는지 (0/1 배낭, 2026-10-08).
        // 계획이 필요한 건 "아직 남은" 일정뿐이다 — 지난 일정에는 계획을 세우지 않는다.
        if (urgent != null && urgent.getDaysLeft() > 0) {
            req.setAttribute("ddayPlan", ddayPlanService.plan(userId, (int) urgent.getDaysLeft()));
            req.setAttribute("planTarget", urgent);
        }
        req.getRequestDispatcher("/WEB-INF/views/dday.jsp").forward(req, resp);
    }

    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }
}
