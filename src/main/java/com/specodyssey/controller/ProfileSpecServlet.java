package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import com.specodyssey.dto.UserSpecDto;
import com.specodyssey.service.ProfileInputChecker;
import com.specodyssey.service.ProfileService;
import com.specodyssey.service.RoadmapService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 보유 스펙 추가 · 수정 · 삭제.
 * 관련 요구사항: FR-23
 * GET ?check=&specType= : 입력하는 동안 명칭 검사(엉터리 글자·자격증 이름 제안) — 결과는 JSON (2026-10-06)
 */
@WebServlet("/profile/specs")
public class ProfileSpecServlet extends HttpServlet {

    private final ProfileService profileService = new ProfileService();
    private final RoadmapService roadmapService = new RoadmapService();
    private final ProfileInputChecker inputChecker = new ProfileInputChecker();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String check = req.getParameter("check");
        if (check == null) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        try {
            ProfileSkillServlet.writeJson(resp, inputChecker.checkSpec(req.getParameter("specType"), check));
        } catch (SQLException e) {
            throw new ServletException("스펙 명칭 확인 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        Long userId = ((UserDto) session.getAttribute("loginUser")).getId();
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                Long specId = Long.valueOf(req.getParameter("specId"));
                profileService.deleteSpec(userId, specId);
            } else if ("update".equals(action)) {
                UserSpecDto spec = parseSpec(req, resp);
                if (spec == null || rejected(req, spec, resp)) {
                    return;
                }
                spec.setId(Long.valueOf(req.getParameter("specId")));
                profileService.updateSpec(userId, spec);
            } else {
                UserSpecDto spec = parseSpec(req, resp);
                if (spec == null || rejected(req, spec, resp)) {
                    return;
                }
                profileService.addSpec(userId, spec);
                // 로드맵 CERT 단계를 거치지 않고 프로필에서 직접 자격증을 추가한 경우 — 일치하는
                // 미완료 CERT 단계가 있으면 로드맵도 같이 완료 처리한다.
                if ("CERT".equals(spec.getSpecType())) {
                    roadmapService.syncCertAddedFromProfile(userId, spec.getTitle());
                }
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 요청입니다.");
            return;
        } catch (SQLException e) {
            throw new ServletException("스펙 저장 중 오류가 발생했습니다.", e);
        }

        resp.sendRedirect(req.getContextPath() + "/profile");
    }

    // 화면의 검사를 거치지 않은 요청도 엉터리 명칭은 저장하지 않는다
    private boolean rejected(HttpServletRequest req, UserSpecDto spec, HttpServletResponse resp)
            throws SQLException, IOException {
        if (!inputChecker.rejectsSpec(spec.getSpecType(), spec.getTitle())) {
            return false;
        }
        ProfileNotice.putError(req, "의미 없는 글자처럼 보여요. 명칭을 다시 확인해 주세요.");
        resp.sendRedirect(req.getContextPath() + "/profile");
        return true;
    }

    // 입력 실수는 프로필 화면에서 안내하고(ProfileNotice) null을 반환한다 — 에러 페이지를 띄우지 않는다.
    private UserSpecDto parseSpec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String title = req.getParameter("title");
        if (title == null || title.isBlank()) {
            ProfileNotice.putError(req, "명칭을 입력해주세요.");
            resp.sendRedirect(req.getContextPath() + "/profile");
            return null;
        }
        UserSpecDto spec = new UserSpecDto();
        spec.setSpecType(req.getParameter("specType"));
        spec.setTitle(title);
        spec.setIssuer(req.getParameter("issuer"));
        spec.setScore(req.getParameter("score"));
        String acquiredDateParam = req.getParameter("acquiredDate");
        if (acquiredDateParam != null && !acquiredDateParam.isBlank()) {
            spec.setAcquiredDate(LocalDate.parse(acquiredDateParam));
        }
        // FR-81 경험(인턴·대외활동·교육)만 기간이 있다 — 취득일 칸이 시작일, 종료일은 비우면 진행 중
        String endDateParam = req.getParameter("endDate");
        if ("EXPERIENCE".equals(spec.getSpecType()) && endDateParam != null && !endDateParam.isBlank()) {
            spec.setEndDate(LocalDate.parse(endDateParam));
            if (spec.getAcquiredDate() != null && spec.getEndDate().isBefore(spec.getAcquiredDate())) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "종료일이 시작일보다 빠릅니다.");
                return null;
            }
        }
        return spec;
    }
}
