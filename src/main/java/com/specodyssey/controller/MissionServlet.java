package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 오늘의 미션(일일 코테) 화면. 관련 요구사항: FR-51~53
 * 화면설계 PDF "11. 일일 미션" 기준 — 화면만(팀 지시). PROBLEM/USER_DAILY_MISSION DAO는 이미
 * 있지만(2주차 이전 뼈대) Service가 없어 여기서는 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/mission")
public class MissionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/mission.jsp").forward(req, resp);
    }
}
