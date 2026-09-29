package com.specodyssey.controller;

import com.specodyssey.dao.JobDao;
import com.specodyssey.dto.JobDto;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * 직무 찾기(설문 + 추천) 화면. 관련 요구사항: FR-34 · 35 · 38 · 39
 * 화면설계 PDF "7. 직무 발굴" 기준 — 설문·추천 산출 로직은 여전히 직무발굴 담당자(youngjun) 몫이라
 * 고정 예시 3개(백엔드 개발자·데이터 엔지니어·DevOps 엔지니어)를 그대로 보여준다.
 *
 * 다만 "이 직무로 격차 분석하기" 버튼만큼은 실제로 눌러서 써야 해서, 그 3개 직무의 진짜 JOB.id를
 * 여기서 찾아 넘겨준다 — 안 그러면 버튼이 셋 다 프로필의 희망 직무만 분석하는 죽은 버튼이 된다.
 */
@WebServlet("/job-discovery")
public class JobDiscoveryServlet extends HttpServlet {

    private static final String[] EXAMPLE_JOB_NAMES = {"백엔드 개발자", "데이터 엔지니어", "DevOps 엔지니어"};

    private final JobDao jobDao = new JobDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            List<JobDto> allJobs = jobDao.findAll();
            for (int i = 0; i < EXAMPLE_JOB_NAMES.length; i++) {
                String name = EXAMPLE_JOB_NAMES[i];
                JobDto match = allJobs.stream()
                        .filter(j -> name.equals(j.getJobName()))
                        .findFirst()
                        .orElse(null);
                req.setAttribute("exampleJob" + (i + 1), match);
            }
        } catch (SQLException e) {
            throw new ServletException("직무 목록을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher("/WEB-INF/views/job-discovery.jsp").forward(req, resp);
    }
}
