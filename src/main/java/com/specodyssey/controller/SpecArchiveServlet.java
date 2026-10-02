package com.specodyssey.controller;

import com.specodyssey.dao.TechArticleDao;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.archive.SpecArchiveService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 스펙 아카이브 목록 — 상위 티어 사용자의 팁 게시판.
 * ?view=bookmarks 내 북마크 / ?sort=popular 하트순(기본 최신순) / ?page=N
 * 글쓰기 버튼은 상위 티어에게만 보이고, 그 아래 티어에게는 몇 티어부터 쓸 수 있는지 안내한다.
 */
@WebServlet("/spec-archive")
public class SpecArchiveServlet extends HttpServlet {

    static final String MESSAGE_KEY = "archiveMessage";
    static final String ERROR_KEY = "archiveError";

    private final SpecArchiveService archiveService = new SpecArchiveService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        boolean bookmarks = "bookmarks".equals(req.getParameter("view"));
        TechArticleDao.Sort sort = "popular".equals(req.getParameter("sort")) ? TechArticleDao.Sort.POPULAR : TechArticleDao.Sort.LATEST;
        int page = parsePage(req.getParameter("page"));

        try {
            req.setAttribute("listPage", bookmarks ? archiveService.listBookmarks(userId, page) : archiveService.list(sort, page));
            req.setAttribute("canWrite", archiveService.canWrite(userId));
            req.setAttribute("writerTitles", String.join("·", archiveService.writerTierTitles()));
        } catch (SQLException e) {
            throw new ServletException("스펙 아카이브를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.setAttribute("view", bookmarks ? "bookmarks" : "all");
        req.setAttribute("sort", sort == TechArticleDao.Sort.POPULAR ? "popular" : "latest");
        moveFlash(req);
        req.getRequestDispatcher("/WEB-INF/views/spec-archive.jsp").forward(req, resp);
    }

    /** 세션에 한 번 담아둔 결과 문구를 이번 화면으로 옮긴다 (새로고침하면 사라진다) */
    static void moveFlash(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        for (String key : new String[] {MESSAGE_KEY, ERROR_KEY}) {
            Object value = session.getAttribute(key);
            if (value != null) {
                session.removeAttribute(key);
                req.setAttribute(key, value);
            }
        }
    }

    private static int parsePage(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    static Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
