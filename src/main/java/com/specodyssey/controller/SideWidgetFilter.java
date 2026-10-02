package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dto.DocumentDto;
import com.specodyssey.dto.TrendTechDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.DailyMissionService;
import com.specodyssey.service.NoteService;
import com.specodyssey.util.AdminAccess;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 화면 좌우에 고정되는 위젯(왼쪽: 일일 미션·최근 서류 보관함, 오른쪽: 오늘의 트렌드 기술·연습장)에 쓸 데이터를 실어 준다.
 * 관련 요구사항: FR-54 트렌드 기술 노출, FR-55 직무 연관 필터링, FR-51 일일 미션
 *
 * 로드맵에만 있던 위젯을 자소서 첨삭·프로필을 뺀 지원자 화면 전체로 넓혔다(사용자 요청, 2026-10-02) — 그 두 화면과 면접관·관리자
 * 화면은 이 필터의 대상 경로가 아니라서 위젯이 없다. 요청에 sideWidgets=true를 실으면 header.jsp가 본문 여백을, footer.jsp가
 * 좌우 영역을 그린다. 항상 세션의 본인 id로만 조회하고, 하나가 실패해도 화면 전체를 깨뜨리지 않고 그 위젯만 빈 상태로 둔다.
 */
@WebFilter(urlPatterns = {
        "/dashboard", "/dday", "/documents", "/gap-analysis", "/insights", "/job-discovery", "/mission",
        "/share-links", "/roadmap", "/spec-archive", "/spec-archive/post", "/spec-archive/write"
})
public class SideWidgetFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SideWidgetFilter.class.getName());
    private static final int TREND_WIDGET_SIZE = 3;
    private static final int RECENT_DOCUMENT_COUNT = 5;

    private final TrendCollectDao trendDao = new TrendCollectDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final DailyMissionService missionService = new DailyMissionService();
    private final NoteService noteService = new NoteService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if ("GET".equals(req.getMethod()) && loginUser instanceof UserDto user && appliesTo(user)) {
            req.setAttribute("sideWidgets", true);
            loadWidgets(req, user);
        }
        chain.doFilter(request, response);
    }

    static boolean appliesTo(UserDto user) {
        return !RoleFilter.INTERVIEWER.equals(user.getUserType()) && !AdminAccess.isAdmin(user);
    }

    private void loadWidgets(HttpServletRequest req, UserDto user) {
        Long userId = user.getId();
        if (user.getDesiredJobId() != null) {
            try {
                List<TrendTechDto> trends = trendDao.findTopByJobId(user.getDesiredJobId(), TREND_WIDGET_SIZE);
                req.setAttribute("trendTechs", trends);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "트렌드 위젯 조회 실패 — 빈 목록으로 표시합니다", e);
            }
        }
        // 오늘의 미션 화면은 MissionProblemFilter가 같은 값을 실어 주므로 중복 조회하지 않는다
        if (!"/mission".equals(req.getServletPath())) {
            try {
                DailyMissionService.TodayMissions today = missionService.getOrAssignToday(userId);
                req.setAttribute("dailyMissions", today.getMissions());
                req.setAttribute("dailyMissionDone", today.getDoneCount());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "일일 미션 위젯 조회 실패 — 빈 상태로 표시합니다", e);
            }
        }
        try {
            List<DocumentDto> documents = documentDao.findByUserId(userId);
            req.setAttribute("recentDocuments", documents.subList(0, Math.min(RECENT_DOCUMENT_COUNT, documents.size())));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "최근 서류 위젯 조회 실패 — 빈 목록으로 표시합니다", e);
        }
        try {
            req.setAttribute("noteText", noteService.load(userId));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "연습장 노트 조회 실패 — 빈 노트로 표시합니다", e);
        }
    }
}
