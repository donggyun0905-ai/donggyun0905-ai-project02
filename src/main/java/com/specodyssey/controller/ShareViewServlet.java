package com.specodyssey.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 면접관 뷰(로그인 없이 링크로만 접근). 관련 요구사항: FR-81~86
 * 화면설계 PDF "13. 면접관 뷰", "14. 면접관 비교 뷰", "6-4. 유효하지 않은 공유 링크" 기준 —
 * 화면만(팀 지시). "/share/*"는 이미 SessionFilter의 PUBLIC_PREFIXES에 있어 로그인 없이 열린다
 * (FR-14 면접관은 계정이 없음). 실제 토큰 검증(ShareLinkDao.findByToken)은 담당자가 붙일 자리라,
 * 여기서는 "demo" 토큰만 유효한 것으로 흉내 내고 나머지는 만료/유효하지 않음 화면을 보여준다.
 */
@WebServlet("/share/*")
public class ShareViewServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        if ("/compare".equals(pathInfo)) {
            req.getRequestDispatcher("/WEB-INF/views/interviewer-compare.jsp").forward(req, resp);
            return;
        }

        String token = (pathInfo == null || pathInfo.length() < 2) ? "" : pathInfo.substring(1);
        req.setAttribute("valid", "demo".equals(token));
        req.getRequestDispatcher("/WEB-INF/views/interviewer-view.jsp").forward(req, resp);
    }
}
