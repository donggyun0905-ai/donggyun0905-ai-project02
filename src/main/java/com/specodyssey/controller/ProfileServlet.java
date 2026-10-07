package com.specodyssey.controller;

import com.specodyssey.dao.DocumentDao;
import com.specodyssey.dao.JobAliasDao;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserProjectDto;
import com.specodyssey.service.AiUsageLogService;
import com.specodyssey.service.EducationService;
import com.specodyssey.service.PersonalInfo;
import com.specodyssey.service.ProfileService;
import com.specodyssey.service.ResumeService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * 프로필 조회 · 기본정보 수정 화면.
 * 관련 요구사항: FR-21 · 22 · 23 · 24 · 25
 */
@WebServlet("/profile")
public class ProfileServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();
    private final JobAliasDao jobAliasDao = new JobAliasDao();
    private final DocumentDao documentDao = new DocumentDao();
    private final ProfileService profileService = new ProfileService();
    private final AiUsageLogService aiUsageLogService = new AiUsageLogService();
    private final ResumeService resumeService = new ResumeService();
    private final EducationService educationService = new EducationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long userId = currentUserId(req);
        try {
            loadProfileAttributes(req, userId);
        } catch (SQLException e) {
            throw new ServletException("프로필을 불러오는 중 오류가 발생했습니다.", e);
        }
        // 기술·스펙 추가에서 리다이렉트로 넘어온 안내 문구 (중복 입력 등) — 한 번만 보여 준다
        ProfileNotice.consume(req);
        req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        Long userId = currentUserId(req);
        if ("confirmJob".equals(req.getParameter("action"))) {
            handleConfirmJob(req, resp, userId);
        } else {
            handleSave(req, resp, userId);
        }
    }

    // 검색창 입력이 정확히 일치해서 어느 직무인지 확실할 때만 바로 저장한다. 편집 거리로 대신
    // 골라낸 애매한 매칭은 여기서 바로 저장하지 않고 확인 화면(pendingJobMatch)을 보여준다 —
    // 희망 직무는 격차분석·로드맵을 좌우하는 값이라 잘못 자동교정되면 위험하기 때문
    // (사용자 요청, 2026-09-29).
    private void handleSave(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException {
        String email = req.getParameter("email");
        String major = req.getParameter("major");
        String interestField = req.getParameter("interestField");
        String desiredJobQuery = req.getParameter("desiredJobQuery");

        try {
            PersonalInfo personalInfo;
            try {
                personalInfo = parsePersonalInfo(req);
            } catch (IllegalArgumentException e) {
                loadProfileAttributes(req, userId);
                req.setAttribute("desiredJobQuery", desiredJobQuery);
                req.setAttribute("errorMessage", e.getMessage());
                req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
                return;
            }

            // 검색창을 비워두면 "아직 모르겠음", 뭐라도 입력하면 "선택함"으로 본다 — 예전엔 이걸
            // 별도 라디오 버튼으로 따로 받았는데, 검색창에 입력만 하고 라디오를 안 누르면 조용히
            // 저장이 안 되는 문제가 있었다(사용자 확인, 2026-09-29). 입력 자체가 곧 의사표시라
            // 라디오 없이 검색창 하나로 판단하는 게 더 자연스럽다.
            Long desiredJobId = null;
            String desiredJobStatus = "UNSET";
            if (desiredJobQuery != null && !desiredJobQuery.isBlank()) {
                // 정식 명칭이든("데이터 엔지니어") 다르게 알고 있는 별칭이든("데이터 프로그래머")
                // 검색창에 입력한 텍스트 하나로 매칭한다(JOB_ALIAS 활용, 팀 합의 2026-09-29).
                ProfileService.JobMatch matched = profileService.resolveJobQuery(desiredJobQuery);
                if (matched == null) {
                    loadProfileAttributes(req, userId);
                    req.setAttribute("desiredJobQuery", desiredJobQuery);
                    req.setAttribute("errorMessage", "입력하신 직무를 찾을 수 없습니다. 목록에 뜨는 이름 중 하나를 선택해주세요.");
                    req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
                    return;
                }
                if (!matched.isExact()) {
                    loadProfileAttributes(req, userId);
                    req.setAttribute("desiredJobQuery", desiredJobQuery);
                    req.setAttribute("pendingJobMatch", matched);
                    // 확인 후 실제로 저장할 때(handleConfirmJob) 같이 반영할 수 있도록 나머지
                    // 기본정보 입력값도 확인 폼의 hidden 필드로 그대로 들고 간다.
                    req.setAttribute("pendingEmail", email);
                    req.setAttribute("pendingMajor", major);
                    req.setAttribute("pendingPersonalInfo", personalInfo);
                    req.setAttribute("pendingInterestField", interestField);
                    req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
                    return;
                }
                desiredJobId = matched.getJob().getId();
                desiredJobStatus = "SET";
            }

            profileService.updateBasicInfo(userId, personalInfo, email, major, interestField,
                    desiredJobId, desiredJobStatus);
            refreshSessionUser(req, userId);
        } catch (SQLException e) {
            throw new ServletException("프로필 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 확인 화면에서 "예, 이 직무로 저장"을 눌렀을 때만 탄다. confirmedJobId는 서버가 직접 렌더링한
    // hidden 필드값이지만, 폼 변조 가능성을 감안해 실제 존재하는(삭제 안 된) 직무인지 다시 검증한다.
    private void handleConfirmJob(HttpServletRequest req, HttpServletResponse resp, Long userId)
            throws ServletException, IOException {
        String email = req.getParameter("email");
        String major = req.getParameter("major");
        String interestField = req.getParameter("interestField");
        String confirmedJobIdParam = req.getParameter("confirmedJobId");

        try {
            Long confirmedJobId = Long.valueOf(confirmedJobIdParam);
            JobDto job = jobDao.findById(confirmedJobId);
            if (job == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
                return;
            }
            // 확인 폼의 hidden 필드로 넘어온 값이라 변조됐을 수 있다 — 다시 검증한다
            profileService.updateBasicInfo(userId, parsePersonalInfo(req), email, major, interestField,
                    job.getId(), "SET");
            refreshSessionUser(req, userId);
        } catch (IllegalArgumentException e) { // NumberFormatException 포함
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("프로필 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    private PersonalInfo parsePersonalInfo(HttpServletRequest req) {
        return PersonalInfo.of(req.getParameter("name"), req.getParameter("age"),
                req.getParameter("careerStatus"), req.getParameter("grade"));
    }

    // 세션에 들고 있는 사본도 최신화 (비밀번호 해시는 세션에 두지 않는다)
    private void refreshSessionUser(HttpServletRequest req, Long userId) throws SQLException {
        UserDto refreshed = userDao.findById(userId);
        refreshed.setPasswordHash(null);
        refreshed.setRecoveryCodeHash(null);
        req.getSession().setAttribute("loginUser", refreshed);
    }

    private void loadProfileAttributes(HttpServletRequest req, Long userId) throws SQLException {
        UserDto user = userDao.findById(userId);
        req.setAttribute("user", user);
        List<JobDto> jobs = jobDao.findAll();
        req.setAttribute("jobs", jobs);
        req.setAttribute("jobAliases", jobAliasDao.findAll());
        if (user.getDesiredJobId() != null) {
            jobs.stream().filter(j -> j.getId().equals(user.getDesiredJobId())).findFirst()
                    .ifPresent(j -> req.setAttribute("desiredJobQuery", j.getJobName()));
        }
        req.setAttribute("education", educationService.find(userId));
        req.setAttribute("specs", profileService.getSpecs(userId));
        List<UserProjectDto> projects = profileService.getProjects(userId);
        req.setAttribute("projects", projects);
        req.setAttribute("projectLinks", profileService.getProjectLinks(projects));
        req.setAttribute("skills", profileService.getSkills(userId));
        // FR-62 프로젝트에 연결된 첨부 파일 조회·다운로드 — PROJECT 로드맵 단계 완료 시 자동 등록된다.
        req.setAttribute("documents", documentDao.findByUserId(userId));
        // FR-101·102(선택) AI 활용 기록 자기 제출.
        req.setAttribute("aiUsageEntries", aiUsageLogService.listMine(userId));
        req.setAttribute("resume", resumeService.findResume(userId));
        req.setAttribute("coverLetter", resumeService.findCoverLetter(userId));
        // 이력서·자소서 올리기·삭제(ProfileResumeServlet)는 끝나면 이 화면으로 돌아온다 — 결과 문구를 한 번만 보여준다
        HttpSession session = req.getSession(false);
        for (String key : new String[] {"resumeMessage", "coverLetterMessage"}) {
            Object message = session.getAttribute(key);
            if (message != null) {
                session.removeAttribute(key);
                req.setAttribute(key, message);
            }
        }
    }

    private Long currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        UserDto loginUser = (UserDto) session.getAttribute("loginUser");
        return loginUser.getId();
    }
}
