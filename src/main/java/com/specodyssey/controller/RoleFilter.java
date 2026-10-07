package com.specodyssey.controller;

import com.specodyssey.dto.UserDto;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * 계정 유형(USERS.user_type)별 접근 범위 필터.
 * 로그인 여부는 SessionFilter가 확인하고, 여기서는 로그인한 사용자가 자기 유형의 화면만 쓰게 한다.
 *
 * - 면접관(INTERVIEWER): "/interviewer/*"(공유받은 이력·지원자 비교·내 프로필)와 공유 링크 열람("/share/*")만.
 *   대시보드·로드맵·미션 같은 지원자 화면은 본인 스펙이 있어야 의미가 있고, 면접관에게는 그 데이터가 없다.
 * - 지원자(APPLICANT): "/interviewer/*"를 쓸 수 없다.
 * - 관리자(ADMIN, 2026-10-06 추가): "/admin/*"만 쓸 수 있다. USERS.user_type에 값만 추가했고
 *   스키마는 안 바꿨다(이미 VARCHAR(15) 자유 값이라 — db-design.md "회의에서 정할 것" 항목에
 *   미리 이 방향으로 정리돼 있었음). 관리자가 아닌 사람이 "/admin/*"에 들어오면 자기 홈으로 돌려보낸다.
 *
 * 허용 목록 방식이라 지원자용 URL이 새로 생겨도 면접관에게는 기본으로 막힌다.
 */
@WebFilter(urlPatterns = {"/*"})
public class RoleFilter implements Filter {

    public static final String INTERVIEWER = "INTERVIEWER";
    public static final String ADMIN = "ADMIN";
    public static final String INTERVIEWER_HOME = "/interviewer/shared";
    public static final String APPLICANT_HOME = "/dashboard";
    public static final String ADMIN_HOME = "/admin";

    // 면접관이 "/interviewer/*" 밖에서 쓸 수 있는 경로
    private static final Set<String> INTERVIEWER_PATHS = Set.of("/logout", "/login", "/register");
    private static final String[] INTERVIEWER_PREFIXES = {
            "/interviewer/", "/share/", "/css/", "/js/", "/img/", "/image/"
    };
    private static final Set<String> COMMON_PATHS = Set.of("/logout", "/login", "/register");
    private static final String[] COMMON_PREFIXES = {"/css/", "/js/", "/img/", "/image/"};

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (loginUser instanceof UserDto user) {
            String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
            boolean admin = ADMIN.equals(user.getUserType());
            boolean interviewer = INTERVIEWER.equals(user.getUserType());

            // 관리자는 "/admin"·"/admin/*"(+ 공통 경로)만 — 지원자·면접관 화면은 본인 스펙이 없어 의미가 없다.
            // ADMIN_HOME("/admin") 자체를 빼먹으면 그 화면에서 다시 자기 자신으로 보내 무한 리다이렉트가 된다.
            if (admin) {
                if (!isAdminPath(path) && !isCommon(path)) {
                    resp.sendRedirect(req.getContextPath() + ADMIN_HOME);
                    return;
                }
                chain.doFilter(request, response);
                return;
            }
            // 관리자가 아니면 "/admin"·"/admin/*"에 못 들어간다.
            if (isAdminPath(path)) {
                resp.sendRedirect(req.getContextPath() + (interviewer ? INTERVIEWER_HOME : APPLICANT_HOME));
                return;
            }
            if (interviewer && !allowedForInterviewer(path)) {
                resp.sendRedirect(req.getContextPath() + INTERVIEWER_HOME);
                return;
            }
            if (!interviewer && path.startsWith("/interviewer/")) {
                resp.sendRedirect(req.getContextPath() + APPLICANT_HOME);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 관리자 화면인지 — "/admin"과 "/admin/*" 둘 다. 두 분기가 같은 기준을 써야 루프가 생기지 않는다. */
    private static boolean isAdminPath(String path) {
        return ADMIN_HOME.equals(path) || path.startsWith(ADMIN_HOME + "/");
    }

    private boolean isCommon(String path) {
        if (COMMON_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : COMMON_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowedForInterviewer(String path) {
        if (INTERVIEWER_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : INTERVIEWER_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
