package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.NoteService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 로드맵 화면 연습장 저장 — 화면의 "저장" 버튼이 fetch로 POST한다. 본인 노트만 다룬다(세션의 loginUser).
 */
@WebServlet("/roadmap-note")
public class RoadmapNoteServlet extends HttpServlet {

    private final NoteService noteService = new NoteService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        UserDto loginUser = (UserDto) req.getSession(false).getAttribute("loginUser");
        try {
            noteService.save(loginUser.getId(), req.getParameter("text"));
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (SQLException e) {
            throw new ServletException("노트를 저장하는 중 오류가 발생했습니다.", e);
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
