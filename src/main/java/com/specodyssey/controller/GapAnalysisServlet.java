package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.GapAnalysisDto;
import com.specodyssey.dto.GapAnalysisItemDto;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.GapAnalysisService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 격차 분석 화면. 관련 요구사항: FR-31 · 42 · 111 · 112
 *
 * ?state=loading|failed|unavailable 는 화면설계 PDF의 AI 대기/실패/데이터없음 상태를 그대로
 * 미리보는 디자인 참고용 파라미터라 남겨뒀다(실제 그 상태로 진입하는 흐름은 아직 없음).
 * 파라미터가 없으면 실제 데이터로 동작한다 — 목표 직무는 기본적으로 프로필의 희망 직무
 * (desiredJobId)를 쓰지만, ?jobId=로 넘어오면 그 직무를 우선한다(직무 찾기 화면에서
 * "이 직무로 격차 분석하기"를 눌렀을 때 프로필 설정과 별개로 그 직무를 바로 분석하기 위함 — 이때
 * 프로필의 희망 직무 자체를 바꾸는 건 아니다, 그건 사용자가 프로필에서 직접 정할 일이다).
 */
@WebServlet("/gap-analysis")
public class GapAnalysisServlet extends HttpServlet {

    private final GapAnalysisService gapAnalysisService = new GapAnalysisService();
    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String state = req.getParameter("state");
        req.setAttribute("state", state);
        if (state != null) {
            // 디자인 참고용 미리보기 상태 — 예시 데이터 그대로 두고 바로 렌더링.
            req.getRequestDispatcher("/WEB-INF/views/gap-analysis.jsp").forward(req, resp);
            return;
        }

        Long userId = currentUserId(req);
        try {
            UserDto user = userDao.findById(userId);
            Long targetJobId = parseJobIdParam(req);
            if (targetJobId == null) {
                targetJobId = user.getDesiredJobId();
            }
            if (targetJobId == null) {
                req.setAttribute("noTargetJob", true);
                req.getRequestDispatcher("/WEB-INF/views/gap-analysis.jsp").forward(req, resp);
                return;
            }

            JobDto job = jobDao.findById(targetJobId);
            GapAnalysisDto analysis = gapAnalysisService.getLatest(userId);
            // 최근 분석이 지금 보려는 직무와 다르면(직무 찾기에서 다른 직무를 골랐거나, 프로필의
            // 희망 직무를 바꾼 경우) 새로 분석해야 의미가 있다.
            if (analysis == null || !analysis.getJobId().equals(targetJobId)) {
                gapAnalysisService.analyze(userId, targetJobId);
                analysis = gapAnalysisService.getLatest(userId);
            }

            List<GapAnalysisItemDto> items = gapAnalysisService.getItems(analysis.getId());
            List<GapItemView> itemViews = new ArrayList<>();
            int metCount = 0;
            for (GapAnalysisItemDto item : items) {
                SkillDto skill = skillDao.findById(item.getSkillId());
                boolean met = "MET".equals(item.getStatus());
                if (met) {
                    metCount++;
                }
                itemViews.add(new GapItemView(skill == null ? "(알 수 없음)" : skill.getSkillName(), met));
            }

            req.setAttribute("job", job);
            req.setAttribute("analysis", analysis);
            req.setAttribute("items", itemViews);
            req.setAttribute("metCount", metCount);
            req.setAttribute("totalCount", items.size());
        } catch (SQLException e) {
            throw new ServletException("격차 분석을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/gap-analysis.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            UserDto user = userDao.findById(userId);
            if (user.getDesiredJobId() == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "희망 직무를 먼저 프로필에서 선택해주세요.");
                return;
            }
            gapAnalysisService.analyze(userId, user.getDesiredJobId());
        } catch (SQLException e) {
            throw new ServletException("격차 분석 처리 중 오류가 발생했습니다.", e);
        }
        resp.sendRedirect(req.getContextPath() + "/gap-analysis");
    }

    // JSP에서 EL로 skillName/met을 바로 읽게 하는 표시 전용 뷰 객체 (DTO를 그대로 안 쓰는 이유는
    // skill_id 대신 이미 조회한 skillName·met 불리언을 바로 쓰게 하기 위함).
    public static final class GapItemView {
        private final String skillName;
        private final boolean met;

        public GapItemView(String skillName, boolean met) {
            this.skillName = skillName;
            this.met = met;
        }

        public String getSkillName() {
            return skillName;
        }

        public boolean isMet() {
            return met;
        }
    }

    // 직무 찾기 화면의 "이 직무로 격차 분석하기" 버튼이 넘기는 jobId — 형식이 이상하면(조작·오류)
    // 그냥 무시하고 프로필의 희망 직무로 넘어간다, 굳이 400을 띄울 정도는 아니다.
    private Long parseJobIdParam(HttpServletRequest req) {
        String raw = req.getParameter("jobId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
