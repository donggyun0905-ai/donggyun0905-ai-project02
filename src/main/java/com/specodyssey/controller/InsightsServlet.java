package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.SpecScoreService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * 데이터 인사이트 화면. 관련 요구사항: FR-45~48
 * 화면설계 PDF "10. 데이터 인사이트" 기준 — 대부분 화면만(팀 지시, 고정 예시 데이터).
 * "또래 비교"(FR-45)만 2026-09-30에 SpecScoreService로 실제 연결한다 — SPEC_SCORE_HISTORY가
 * 이 항목의 데이터 소스이기 때문이다. 나머지(취업시장 트렌드 상세·합격자 참고 루트·약점 히트맵)는
 * FR-46·48 등 아직 담당자가 없는 별개 작업이라 손대지 않는다.
 */
@WebServlet("/insights")
public class InsightsServlet extends HttpServlet {

    private final SpecScoreService specScoreService = new SpecScoreService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Long userId = currentUserId(req);
            SpecScoreService.PeerComparison comparison = specScoreService.getPeerComparison(userId);
            req.setAttribute("peerComparison", comparison);
            // JSP는 출력만 한다(claude.md) — 비교 문구는 여기서 만들어 넘긴다.
            if (comparison != null && comparison.peerAverage() != null) {
                BigDecimal diff = comparison.myScore().subtract(comparison.peerAverage());
                String message = diff.signum() > 0
                        ? "평균보다 " + diff.abs() + "점 높습니다."
                        : diff.signum() < 0
                        ? "평균보다 " + diff.abs() + "점 낮습니다."
                        : "평균과 같습니다.";
                req.setAttribute("peerDiffMessage", message);
            }
        } catch (SQLException e) {
            throw new ServletException("데이터 인사이트를 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/insights.jsp").forward(req, resp);
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
