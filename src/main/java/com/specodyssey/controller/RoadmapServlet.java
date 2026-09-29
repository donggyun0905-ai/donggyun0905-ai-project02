package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 로드맵 화면 자리. 관련 요구사항: FR-32 · 33 · 36
 *
 * 지금은 화면만(팀 지시) — "roadmap" 속성을 아예 안 채우므로 roadmap.jsp가 자동으로
 * "아직 생성된 로드맵이 없습니다" 빈 상태를 보여준다(그 JSP에 이미 있는 분기라 별도 처리 불필요).
 * 실제 생성·완료 체크 로직(RoadmapService 등)은 로드맵 담당자 브랜치에 있고, 이후 합칠 때
 * 이 파일을 그 버전으로 교체하면 된다 — 지금은 doPost가 없어 "완료하기" 등 폼 제출은
 * 405(Method Not Allowed)로 응답한다.
 */
@WebServlet("/roadmap")
public class RoadmapServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/roadmap.jsp").forward(req, resp);
    }
}
