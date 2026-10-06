package com.specodyssey.controller;

import com.specodyssey.dto.InterviewerCompareDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.CompareExcelExporter;
import com.specodyssey.service.CompareSort;
import com.specodyssey.service.InterviewerService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 면접관 계정 화면 — 공유받은 이력("/interviewer/shared"), 지원자 비교("/interviewer/compare"),
 * 내 프로필("/interviewer/profile"). 관련 요구사항: FR-82 · 83
 * 면접관 계정만 들어올 수 있다(RoleFilter). 항상 세션의 본인 비교 목록만 다룬다.
 */
@WebServlet("/interviewer/*")
public class InterviewerServlet extends HttpServlet {

    private static final String SHARED = "/shared";
    private static final String COMPARE = "/compare";
    private static final String PROFILE = "/profile";
    private static final String COMPARE_EXPORT = "/compare/export";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    // 나란히 보기 정렬 기준 — 역량을 추가·삭제하고 돌아와도 고른 기준이 유지되게 세션에 둔다
    private static final String SORT_SESSION_KEY = "interviewerCompareSort";

    private final InterviewerService interviewerService = new InterviewerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            show(req, resp, req.getPathInfo());
        } catch (SQLException e) {
            throw new ServletException("면접관 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        String page = req.getPathInfo();
        String action = req.getParameter("action");

        try {
            try {
                if (SHARED.equals(page) && "add".equals(action)) {
                    interviewerService.addLink(userId, req.getParameter("link"));
                } else if (SHARED.equals(page) && "remove".equals(action)) {
                    interviewerService.removeItem(userId, Long.valueOf(req.getParameter("itemId")));
                } else if (COMPARE.equals(page) && "addCriterion".equals(action)) {
                    interviewerService.saveCriterion(userId, req.getParameter("skillName"),
                            Integer.parseInt(req.getParameter("weight")));
                } else if (COMPARE.equals(page) && "removeCriterion".equals(action)) {
                    interviewerService.removeCriterion(userId, Long.valueOf(req.getParameter("criterionId")));
                } else if (PROFILE.equals(page)) {
                    interviewerService.updateProfile(userId, req.getParameter("name"),
                            req.getParameter("email"), req.getParameter("companyName"));
                    req.getSession(false).setAttribute("interviewerNotice", "저장했습니다.");
                } else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                    return;
                }
            } catch (NumberFormatException e) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            } catch (IllegalArgumentException e) {
                req.setAttribute("errorMessage", e.getMessage());
                show(req, resp, page);
                return;
            }
        } catch (SQLException e) {
            throw new ServletException("면접관 화면 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/interviewer" + page);
    }

    private void show(HttpServletRequest req, HttpServletResponse resp, String page)
            throws SQLException, ServletException, IOException {
        Long userId = loginUserId(req);
        if (SHARED.equals(page)) {
            req.setAttribute("compare", interviewerService.loadCompare(userId));
            forward(req, resp, "interviewer-shared.jsp");
        } else if (COMPARE.equals(page)) {
            CompareSort sort = compareSort(req);
            req.setAttribute("compare", interviewerService.loadCompare(userId, sort));
            req.setAttribute("sortOptions", CompareSort.values());
            req.setAttribute("currentSort", sort.getKey());
            req.setAttribute("skills", interviewerService.listSkills());
            req.setAttribute("company", interviewerService.getOrCreateSession(userId).getCompanyName());
            forward(req, resp, "interviewer-compare.jsp");
        } else if (PROFILE.equals(page)) {
            Object notice = req.getSession(false).getAttribute("interviewerNotice");
            if (notice != null) {
                req.getSession(false).removeAttribute("interviewerNotice");
                req.setAttribute("notice", notice);
            }
            req.setAttribute("user", interviewerService.findUser(userId));
            req.setAttribute("company", interviewerService.getOrCreateSession(userId).getCompanyName());
            forward(req, resp, "interviewer-profile.jsp");
        } else if (COMPARE_EXPORT.equals(page)) {
            exportExcel(resp, interviewerService.loadCompare(userId, compareSort(req)));
        } else {
            resp.sendRedirect(req.getContextPath() + RoleFilter.INTERVIEWER_HOME);
        }
    }

    /**
     * 담은 지원자 스펙을 엑셀(.xlsx)로 내려준다 — 화면에서 고른 정렬 순서 그대로, 공개한 값만.
     * 열 너비를 글 길이에 맞춰 두어 처음 열었을 때 글이 잘려 보이지 않는다. 파일명에 받은 날짜를 넣는다.
     */
    private static void exportExcel(HttpServletResponse resp, InterviewerCompareDto compare) throws IOException {
        byte[] body = CompareExcelExporter.toXlsx(compare);
        String fileName = "지원자비교_" + LocalDate.now(ZONE) + ".xlsx";
        resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        resp.setHeader("Content-Disposition", "attachment; filename=\"applicants.xlsx\"; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        resp.setHeader("Cache-Control", "no-store"); // 지원자 개인정보 — 브라우저·프록시에 남기지 않는다
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    // ?sort=로 고르면 세션에 기억하고, 없으면 마지막으로 고른 기준(처음엔 담은 순서)
    private static CompareSort compareSort(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        String param = req.getParameter("sort");
        if (param != null && session != null) {
            session.setAttribute(SORT_SESSION_KEY, CompareSort.fromKey(param).getKey());
        }
        Object saved = session == null ? null : session.getAttribute(SORT_SESSION_KEY);
        return CompareSort.fromKey(param != null ? param : saved == null ? null : saved.toString());
    }

    private void forward(HttpServletRequest req, HttpServletResponse resp, String jsp)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/" + jsp).forward(req, resp);
    }

    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }
}
