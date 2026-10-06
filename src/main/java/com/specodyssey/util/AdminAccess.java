package com.specodyssey.util;

import com.specodyssey.dto.UserDto;

/**
 * 관리자 여부 — USERS.user_type = 'ADMIN'인지만 본다(2026-10-06).
 * user_type은 면접관 계정(TD-4) 때 이미 VARCHAR(15) 자유 값으로 열어둔 컬럼이라 스키마 변경 없이
 * 'ADMIN' 값만 추가했다(db-design.md "회의에서 정할 것" 항목에 이미 이 방향으로 정리돼 있었음).
 * 예전에는 로그인 아이디가 ADMIN_LOGIN_ID(.env)와 같은지만 봤는데, 실제 역할 값이 생겼으니 그걸 쓴다.
 * 관리자 화면·메뉴·로그인 후 이동이 모두 이 한 곳을 쓴다.
 */
public final class AdminAccess {

    public static final String ADMIN_USER_TYPE = "ADMIN";

    private AdminAccess() {
    }

    public static boolean isAdmin(UserDto user) {
        return user != null && ADMIN_USER_TYPE.equals(user.getUserType());
    }
}
