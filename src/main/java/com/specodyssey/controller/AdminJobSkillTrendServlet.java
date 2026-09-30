package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.JobSkillTrendDao;
import com.specodyssey.dao.SkillDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.JobSkillTrendDto;
import com.specodyssey.dto.SkillDto;
import com.specodyssey.service.JobSkillTrendService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * [임시] JOB_SKILL_TREND 수동 재집계 화면 — 팀 결정 항목 2-1.
 * 관련 요구사항: FR-47. LLM 없이 JOB_POSTING.tech_stack을 규칙 기반으로 다시 집계하는
 * JobSkillTrendService.refreshAll()을 수동으로 돌려보기 위한 용도다.
 *
 * 정식 관리자 권한 체계(역할 구분 등)가 아직 없어서 "로그인만 하면 접근 가능"한 상태로 임시로
 * 만든다 — SessionFilter 기본 정책(로그인 필요)만 적용되고 별도 관리자 체크는 없다.
 * 화면을 보면서 계속 다듬기로 했으므로(2026-09-30), 정식 배포 전에 접근 제한을 다시 검토해야 한다.
 */
@WebServlet("/admin/job-skill-trend")
public class AdminJobSkillTrendServlet extends HttpServlet {

    private static final int TOP_N = 15;

    private final JobSkillTrendService jobSkillTrendService = new JobSkillTrendService();
    private final JobSkillTrendDao jobSkillTrendDao = new JobSkillTrendDao();
    private final JobDao jobDao = new JobDao();
    private final SkillDao skillDao = new SkillDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            loadSummary(req);
        } catch (SQLException e) {
            throw new ServletException("현황을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/admin-job-skill-trend.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            int upserted = jobSkillTrendService.refreshAll();
            req.setAttribute("resultMessage", upserted + "건 갱신했습니다.");
            loadSummary(req);
        } catch (SQLException e) {
            req.setAttribute("resultMessage", "갱신 중 오류가 발생했습니다: " + e.getMessage());
            try {
                loadSummary(req);
            } catch (SQLException inner) {
                throw new ServletException("현황을 불러오는 중 오류가 발생했습니다.", inner);
            }
        }
        req.getRequestDispatcher("/WEB-INF/views/admin-job-skill-trend.jsp").forward(req, resp);
    }

    private void loadSummary(HttpServletRequest req) throws SQLException {
        List<JobSkillTrendDto> all = jobSkillTrendDao.findAll();
        req.setAttribute("totalRows", all.size());

        TreeSet<String> periods = all.stream().map(JobSkillTrendDto::getPeriodYm)
                .collect(Collectors.toCollection(TreeSet::new));
        req.setAttribute("periodCount", periods.size());
        String latestPeriod = periods.isEmpty() ? null : periods.last();
        req.setAttribute("latestPeriod", latestPeriod);

        if (latestPeriod == null) {
            req.setAttribute("topRows", List.of());
            return;
        }

        Map<Long, JobDto> jobCache = new HashMap<>();
        Map<Long, SkillDto> skillCache = new HashMap<>();
        List<Map<String, Object>> topRows = all.stream()
                .filter(t -> latestPeriod.equals(t.getPeriodYm()))
                .sorted(Comparator.comparing(JobSkillTrendDto::getMentionCount).reversed())
                .limit(TOP_N)
                .map(t -> {
                    JobDto job = jobCache.computeIfAbsent(t.getJobId(), this::findJobQuietly);
                    SkillDto skill = skillCache.computeIfAbsent(t.getSkillId(), this::findSkillQuietly);
                    Map<String, Object> row = new HashMap<>();
                    row.put("jobName", job == null ? "(알 수 없음)" : job.getJobName());
                    row.put("skillName", skill == null ? "(알 수 없음)" : skill.getSkillName());
                    row.put("mentionCount", t.getMentionCount());
                    row.put("mentionRatio", t.getMentionRatio());
                    return row;
                })
                .collect(Collectors.toList());
        req.setAttribute("topRows", topRows);
    }

    private JobDto findJobQuietly(Long jobId) {
        try {
            return jobDao.findById(jobId);
        } catch (SQLException e) {
            return null;
        }
    }

    private SkillDto findSkillQuietly(Long skillId) {
        try {
            return skillDao.findById(skillId);
        } catch (SQLException e) {
            return null;
        }
    }
}
