package com.specodyssey.controller;

import com.specodyssey.service.archive.SpecArchiveRules;
import com.specodyssey.service.archive.SpecArchiveService;
import com.specodyssey.service.archive.SpecArchiveService.PostDetail;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 스펙 아카이브 글 상세 — 본문·첨부·하트·북마크·댓글.
 * GET ?id=N 보기. POST action = like / bookmark / comment / deleteComment / deletePost, 처리 후 다시 GET으로 보낸다(PRG).
 * 지우기는 서비스·DAO가 작성자 본인인지 확인한다 — 다른 사람 id를 넘겨도 지워지지 않는다.
 */
@WebServlet("/spec-archive/post")
public class SpecArchivePostServlet extends HttpServlet {

    private final SpecArchiveService archiveService = new SpecArchiveService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = SpecArchiveServlet.currentUserId(req);
        Long articleId = parseId(req.getParameter("id"));
        PostDetail detail = null;
        try {
            if (articleId != null) {
                detail = archiveService.detail(articleId, userId);
            }
        } catch (SQLException e) {
            throw new ServletException("글을 불러오는 중 오류가 발생했습니다.", e);
        }
        if (detail == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        req.setAttribute("detail", detail);
        req.setAttribute("commentMax", SpecArchiveRules.COMMENT_MAX);
        SpecArchiveServlet.moveFlash(req);
        req.getRequestDispatcher("/WEB-INF/views/spec-archive-post.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = SpecArchiveServlet.currentUserId(req);
        Long articleId = parseId(req.getParameter("id"));
        String action = req.getParameter("action");
        HttpSession session = req.getSession();
        String anchor = "";

        if (articleId == null) {
            resp.sendRedirect(req.getContextPath() + "/spec-archive");
            return;
        }
        try {
            switch (action == null ? "" : action) {
                case "like" -> {
                    archiveService.toggleLike(userId, articleId);
                    anchor = "#reactions";
                }
                case "bookmark" -> {
                    boolean on = archiveService.toggleBookmark(userId, articleId);
                    session.setAttribute(SpecArchiveServlet.MESSAGE_KEY, on ? "북마크에 담았습니다." : "북마크에서 뺐습니다.");
                    anchor = "#reactions";
                }
                case "comment" -> {
                    archiveService.addComment(userId, articleId, req.getParameter("content"), parseId(req.getParameter("replyTo")));
                    anchor = "#comments";
                }
                case "deleteComment" -> {
                    boolean removed = archiveService.deleteComment(userId, parseId(req.getParameter("commentId")));
                    if (!removed) {
                        session.setAttribute(SpecArchiveServlet.ERROR_KEY, "지울 댓글을 찾을 수 없습니다.");
                    }
                    anchor = "#comments";
                }
                case "deletePost" -> {
                    boolean removed = archiveService.deletePost(userId, articleId);
                    session.setAttribute(removed ? SpecArchiveServlet.MESSAGE_KEY : SpecArchiveServlet.ERROR_KEY,
                            removed ? "글을 지웠습니다." : "지울 글을 찾을 수 없습니다.");
                    resp.sendRedirect(req.getContextPath() + "/spec-archive");
                    return;
                }
                default -> session.setAttribute(SpecArchiveServlet.ERROR_KEY, "잘못된 요청입니다.");
            }
        } catch (IllegalArgumentException e) {
            session.setAttribute(SpecArchiveServlet.ERROR_KEY, e.getMessage());
            anchor = "comment".equals(action) ? "#comments" : "";
        } catch (SQLException e) {
            throw new ServletException("요청을 처리하는 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/spec-archive/post?id=" + articleId + anchor);
    }

    private static Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
