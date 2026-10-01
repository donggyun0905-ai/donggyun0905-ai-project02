package com.specodyssey.controller;

import com.google.gson.Gson;
import com.specodyssey.dao.JobDao;
import com.specodyssey.dao.UserDao;
import com.specodyssey.dto.JobDto;
import com.specodyssey.dto.UserDto;
import com.specodyssey.service.ResumeFeedbackService;
import com.specodyssey.service.ResumeFeedbackService.DocType;
import com.specodyssey.service.ResumeFeedbackService.Feedback;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 자소서·이력서 첨삭 화면. 관련 요구사항: FR-91 · 92 · 112
 * GET: 입력 화면(목표 직무 기본값 = 프로필의 희망 직무). POST: 첨삭을 받아 같은 화면에 결과를 보여준다.
 * 결과는 비교 화면(js/resume-feedback.js)이 원문 위에 "삭제·추가"로 겹쳐 보여주고, 사용자가 골라 적용하면 입력 칸이 바뀐다.
 * 결과와 본문은 저장하지 않는다 — 화면을 벗어나면 사라진다.
 */
@WebServlet("/resume-feedback")
public class ResumeFeedbackServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ResumeFeedbackServlet.class.getName());
    private static final String VIEW = "/WEB-INF/views/resume-feedback.jsp";
    // LLM 비용·무료 한도 보호 — 같은 사용자가 연달아 누르는 것을 막는다
    private static final long MIN_INTERVAL_MS = 10_000;
    private static final String LAST_REQUEST_AT = "resumeFeedbackLastAt";
    private static final Gson GSON = new Gson();

    private final ResumeFeedbackService feedbackService = new ResumeFeedbackService();
    private final UserDao userDao = new UserDao();
    private final JobDao jobDao = new JobDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            UserDto user = userDao.findById(currentUserId(req));
            prepareForm(req, user == null ? null : user.getDesiredJobId(), DocType.COVER_LETTER, "");
        } catch (SQLException e) {
            throw new ServletException("첨삭 화면을 불러오는 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String text = ResumeFeedbackService.normalizeNewlines(req.getParameter("content"));
        DocType docType = DocType.from(req.getParameter("docType"));
        Long jobId = parseLong(req.getParameter("jobId"));

        try {
            prepareForm(req, jobId, docType == null ? DocType.COVER_LETTER : docType, text);
            JobDto job = jobId == null ? null : jobDao.findById(jobId);

            String error = validate(docType, job, text);
            if (error == null) {
                error = checkInterval(req.getSession());
            }
            if (error != null) {
                req.setAttribute("errorMessage", error);
            } else {
                Feedback feedback = feedbackService.review(docType, job, text);
                req.setAttribute("feedback", feedback);
                req.setAttribute("feedbackJob", job);
                // 비교 화면 스크립트가 읽는 데이터. Gson 기본 설정은 < > & ' = 를 \\u003c 등으로 바꿔 써서
                // <script type="application/json"> 안에 그대로 넣어도 태그가 깨지거나 스크립트가 끼어들 수 없다.
                req.setAttribute("feedbackJson", GSON.toJson(Map.of("text", feedback.text(), "items", feedback.items())));
            }
        } catch (ExternalApiException e) {
            // FR-112 — 실패해도 화면은 그대로, 입력한 글도 그대로 남긴다. 본문은 로그에 남기지 않는다.
            LOG.log(Level.WARNING, "첨삭 LLM 호출 실패 (status=" + e.getStatusCode() + ")", e);
            req.setAttribute("errorMessage", "지금은 첨삭을 받을 수 없습니다. 잠시 후 다시 시도해 주세요. 입력한 글은 그대로 남아 있습니다.");
        } catch (SQLException e) {
            throw new ServletException("첨삭 처리 중 오류가 발생했습니다.", e);
        }
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    private void prepareForm(HttpServletRequest req, Long selectedJobId, DocType docType, String text) throws SQLException {
        List<JobDto> jobs = jobDao.findAll();
        req.setAttribute("jobs", jobs);
        req.setAttribute("selectedJobId", selectedJobId);
        req.setAttribute("docTypes", DocType.values());
        req.setAttribute("selectedDocType", docType.name());
        req.setAttribute("content", text);
        req.setAttribute("maxLength", ResumeFeedbackService.MAX_TEXT_LENGTH);
    }

    private static String validate(DocType docType, JobDto job, String text) {
        if (docType == null) {
            return "글 종류를 골라 주세요.";
        }
        if (job == null) {
            return "목표 직무를 골라 주세요.";
        }
        if (text.length() < ResumeFeedbackService.MIN_TEXT_LENGTH) {
            return "첨삭할 글을 " + ResumeFeedbackService.MIN_TEXT_LENGTH + "자 이상 입력해 주세요.";
        }
        if (text.length() > ResumeFeedbackService.MAX_TEXT_LENGTH) {
            return "글은 " + ResumeFeedbackService.MAX_TEXT_LENGTH + "자까지 첨삭할 수 있습니다. (지금 " + text.length() + "자)";
        }
        return null;
    }

    private static String checkInterval(HttpSession session) {
        long now = System.currentTimeMillis();
        Long last = (Long) session.getAttribute(LAST_REQUEST_AT);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            long wait = (MIN_INTERVAL_MS - (now - last) + 999) / 1000;
            return wait + "초 뒤에 다시 시도해 주세요.";
        }
        session.setAttribute(LAST_REQUEST_AT, now);
        return null;
    }

    private static Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
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
