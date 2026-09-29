package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 직무 찾기(설문 + 추천) 화면. 관련 요구사항: FR-34 · 35 · 38 · 39
 * 화면설계 PDF "7. 직무 발굴" 기준 — 화면만(팀 지시). 실제 설문 채점·추천 로직은 직무발굴
 * 담당자(youngjun) 몫이라 여기서는 고정 예시 데이터로 렌더링한다.
 */
@WebServlet("/job-discovery")
public class JobDiscoveryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/job-discovery.jsp").forward(req, resp);
    }
}
