package com.specodyssey.controller;

import com.specodyssey.dto.ShareLinkDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ShareLinkService;

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
 * 공유 링크 관리 화면(지원자가 로그인 상태에서 발급·관리). 관련 요구사항: FR-85 · 86
 * 화면설계 PDF "12. 공유 링크 관리" 기준.
 * 면접관이 로그인 없이 보는 화면은 ShareViewServlet("/share/*")이 따로 처리한다.
 */
@WebServlet("/share-links")
public class ShareLinksServlet extends HttpServlet {

    // 새로고침으로 링크가 또 만들어지지 않게 POST 뒤에 리다이렉트하므로, 방금 만든 링크는 세션에 잠깐 실어 나른다
    private static final String CREATED_LINK_KEY = "createdShareLink";
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ShareLinkService shareLinkService = new ShareLinkService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        ShareLinkDto created = (ShareLinkDto) session.getAttribute(CREATED_LINK_KEY);
        if (created != null) {
            session.removeAttribute(CREATED_LINK_KEY);
            req.setAttribute("createdLink", created);
            if (created.getExpiresAt() != null) {
                req.setAttribute("createdExpiresAt", created.getExpiresAt().format(EXPIRES_FORMAT));
            }
        }
        try {
            showPage(req, resp, loginUserId(req));
        } catch (SQLException e) {
            throw new ServletException("공유 링크를 불러오는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = loginUserId(req);
        String action = req.getParameter("action");

        try {
            if ("stop".equals(action)) {
                // FR-86 공유 중단
                shareLinkService.setActive(userId, Long.valueOf(req.getParameter("linkId")), false);
            } else if ("resume".equals(action)) {
                shareLinkService.setActive(userId, Long.valueOf(req.getParameter("linkId")), true);
            } else if ("delete".equals(action)) {
                shareLinkService.deleteLink(userId, Long.valueOf(req.getParameter("linkId")));
            } else {
                // FR-85 새 링크 만들기
                int expiryDays = Integer.parseInt(req.getParameter("expiryDays"));
                ShareLinkDto created;
                try {
                    created = shareLinkService.createLink(
                            userId,
                            req.getParameter("label"),
                            expiryDays == 0 ? null : expiryDays, // 0 = 만료 없음
                            req.getParameter("scopeBasic") != null,
                            req.getParameter("scopeSkills") != null,
                            req.getParameter("scopeGrowth") != null,
                            req.getParameter("scopeResume") != null);
                } catch (IllegalArgumentException e) {
                    req.setAttribute("errorMessage", e.getMessage());
                    showPage(req, resp, userId);
                    return;
                }
                req.getSession(false).setAttribute(CREATED_LINK_KEY, created);
            }
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("공유 링크 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/share-links");
    }

    private void showPage(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws SQLException, ServletException, IOException {
        req.setAttribute("links", shareLinkService.listLinks(userId));
        req.setAttribute("shareBaseUrl", shareBaseUrl(req));
        req.getRequestDispatcher("/WEB-INF/views/share-links.jsp").forward(req, resp);
    }

    private Long loginUserId(HttpServletRequest req) {
        return ((UserDto) req.getSession(false).getAttribute("loginUser")).getId();
    }

    // 면접관에게 보낼 주소의 토큰 앞부분 — 지금 접속한 호스트 기준으로 만든다
    private String shareBaseUrl(HttpServletRequest req) {
        StringBuilder url = new StringBuilder(req.getScheme()).append("://").append(req.getServerName());
        boolean defaultPort = ("http".equals(req.getScheme()) && req.getServerPort() == 80)
                || ("https".equals(req.getScheme()) && req.getServerPort() == 443);
        if (!defaultPort) {
            url.append(':').append(req.getServerPort());
        }
        return url.append(req.getContextPath()).append("/share/").toString();
    }
}
