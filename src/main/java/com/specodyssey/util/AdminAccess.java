package com.specodyssey.util;

import com.specodyssey.dto.UserDto;

/**
 * 관리자 여부 — 정식 역할(USERS.role)이 아직 없어서(스키마 변경은 팀 확인 필요) 로그인 아이디가
 * ADMIN_LOGIN_ID(.env, 기본값 "admin")와 같은지만 본다. 관리자 화면·메뉴·로그인 후 이동이 모두 이 한 곳을 쓴다.
 */
public final class AdminAccess {

    private static final String DEFAULT_ADMIN_LOGIN_ID = "admin";

    private AdminAccess() {
    }

    public static boolean isAdmin(UserDto user) {
        if (user == null || user.getLoginId() == null) {
            return false;
        }
        String adminLoginId = AppConfig.get("ADMIN_LOGIN_ID");
        if (adminLoginId == null || adminLoginId.isBlank()) {
            adminLoginId = DEFAULT_ADMIN_LOGIN_ID;
        }
        return adminLoginId.equals(user.getLoginId());
    }
}
