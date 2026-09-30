package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.service.DailyMissionService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 미션 화면("/mission")에 "오늘의 추천 문제"를 실어 준다.
 * 관련 요구사항: FR-51 일일 미션, FR-52 난이도 조정
 *
 * MissionServlet(화면 담당 팀원 파일)을 고치지 않으려고 필터로 처리한다. 항상 세션의 본인 id로만 조회하고,
 * 실패해도 화면 전체를 깨뜨리지 않는다(문제 카드는 빈 상태 안내로 대체된다).
 */
@WebFilter(urlPatterns = {"/mission"})
public class MissionProblemFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(MissionProblemFilter.class.getName());

    private final DailyMissionService missionService = new DailyMissionService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if ("GET".equals(req.getMethod()) && loginUser instanceof UserDto user) {
            try {
                DailyMissionService.TodayMissions today = missionService.getOrAssignToday(user.getId());
                req.setAttribute("dailyMissions", today.getMissions());
                req.setAttribute("dailyMissionDone", today.getDoneCount());
                req.setAttribute("dailyMissionToday", today);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "오늘의 미션 조회 실패 — 빈 목록으로 표시합니다", e);
            }
        }
        chain.doFilter(request, response);
    }
}
