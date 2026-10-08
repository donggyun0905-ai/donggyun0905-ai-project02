package com.specodyssey.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionFilter의 공개 경로 목록을 잠가 두는 테스트 (2026-10-08).
 *
 * claude.md는 "SessionFilter 공개 경로 변경 시 리뷰어 2명 (보안 경계)"을 요구한다. 사람이 기억해야
 * 지켜지는 규칙이라, 목록이 늘거나 줄면 이 테스트가 먼저 깨지게 만들어 둔다. 고치려면 아래 EXPECTED를
 * 같이 고쳐야 하고, 그러면 PR diff에 "보안 경계를 건드렸다"가 분명히 드러난다 — 리뷰에서 놓칠 수 없다.
 *
 * 왜 필요한가: 이 목록에 경로를 넣으면 그 아래 모든 요청이 로그인 검사를 건너뛴다. "/api/companion/"이
 * 아니라 "/api/"로 한 글자 짧게 적으면 사이트의 모든 API가 인증 없이 열린다. 돌려받는 게 500이 아니라
 * "조용히 열린 문"이라 화면만 보고는 알 수 없다. 그래서 빌드에서 막는다.
 */
class SessionFilterPublicPathTest {

    /** 지금 공개하기로 **검토를 거쳐** 합의한 목록. 바꿀 때는 리뷰어 2명. */
    private static final Set<String> EXPECTED_PREFIXES = new LinkedHashSet<>(List.of(
            "/css/", "/js/", "/img/", "/image/", "/share/", "/api/companion/"
    ));

    private static final Set<String> EXPECTED_PATHS = Set.of(
            "/", "/index.jsp", "/login", "/register", "/password-reset", "/recovery-code"
    );

    /**
     * 한 칸(세그먼트)짜리로 열면 그 아래 전부가 열리므로, 정적 자산이 아닌 이 뿌리들은 통째로 공개할 수 없다.
     * 더 깊은 경로("/api/companion/")는 괜찮다 — 범위가 그 서블릿 하나로 좁다.
     */
    private static final Set<String> FORBIDDEN_ROOTS = Set.of(
            "/api/", "/admin/", "/profile/", "/roadmap/", "/analysis/", "/dashboard/",
            "/documents/", "/notifications/", "/companion/", "/mission/", "/simulation/"
    );

    @Test
    @DisplayName("공개 경로 목록이 합의한 것과 똑같다 — 바뀌면 리뷰어 2명을 거쳤는지 확인하라")
    void 공개_경로_목록이_합의한_것과_같다() throws Exception {
        assertEquals(EXPECTED_PREFIXES, new LinkedHashSet<>(Arrays.asList(prefixes())),
                "SessionFilter.PUBLIC_PREFIXES가 바뀌었다. 로그인 없이 열리는 범위가 달라졌다는 뜻이다. "
                        + "claude.md: 보안 경계 변경은 리뷰어 2명. 의도한 변경이면 이 테스트의 EXPECTED_PREFIXES도 함께 고쳐라.");
        assertEquals(EXPECTED_PATHS, paths(),
                "SessionFilter.PUBLIC_PATHS가 바뀌었다. 위와 같다 — 리뷰어 2명.");
    }

    @Test
    @DisplayName("공개 접두사는 / 로 시작하고 / 로 끝난다")
    void 접두사는_슬래시로_감싸여_있다() throws Exception {
        for (String prefix : prefixes()) {
            assertTrue(prefix.startsWith("/"), prefix + " — / 로 시작해야 한다");
            assertTrue(prefix.endsWith("/"),
                    prefix + " — / 로 끝나야 한다. 없으면 startsWith가 옆 경로까지 먹는다 "
                            + "(예: \"/share\"는 \"/shared-secrets\"도 통과시킨다)");
            assertFalse("/".equals(prefix), "\"/\" 는 사이트 전체를 여는 것이다");
        }
    }

    @Test
    @DisplayName("인증이 필요한 뿌리 경로를 통째로 공개하지 않는다 (/api/ 같은 것)")
    void 위험한_뿌리_경로는_공개하지_않는다() throws Exception {
        for (String prefix : prefixes()) {
            assertFalse(FORBIDDEN_ROOTS.contains(prefix),
                    prefix + " — 이 뿌리를 통째로 공개하면 그 아래 모든 서블릿이 로그인 없이 열린다. "
                            + "열어야 하는 서블릿 하나까지 좁혀서 적어라 (예: \"/api/\" 대신 \"/api/companion/\").");
        }
    }

    @Test
    @DisplayName("공개 접두사끼리 서로를 덮지 않는다 — 넓은 쪽이 몰래 들어와도 잡는다")
    void 접두사가_서로를_덮지_않는다() throws Exception {
        String[] all = prefixes();
        for (String a : all) {
            for (String b : all) {
                if (!a.equals(b)) {
                    assertFalse(b.startsWith(a),
                            a + " 가 " + b + " 를 이미 덮고 있다. 넓은 쪽(" + a + ")이 실수로 들어온 것이 아닌지 봐라.");
                }
            }
        }
    }

    @Test
    @DisplayName("보호된 경로는 여전히 보호된다 — 대표 화면 몇 개를 직접 물어본다")
    void 보호된_경로는_공개가_아니다() throws Exception {
        for (String path : List.of("/dashboard", "/profile", "/roadmap", "/admin", "/admin/companion",
                "/api/companion", "/api/other", "/documents", "/companion/connect", "/shared-secrets")) {
            assertFalse(isPublic(path), path + " 가 로그인 없이 열린다");
        }
    }

    @Test
    @DisplayName("공개여야 하는 경로는 공개다")
    void 공개여야_하는_경로는_공개다() throws Exception {
        for (String path : List.of("/login", "/register", "/css/style.css", "/image/logo.png",
                "/share/abc123", "/api/companion/token", "/api/companion/messages")) {
            assertTrue(isPublic(path), path + " 가 막혀 있다");
        }
    }

    private static String[] prefixes() throws Exception {
        Field f = SessionFilter.class.getDeclaredField("PUBLIC_PREFIXES");
        f.setAccessible(true);
        return (String[]) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> paths() throws Exception {
        Field f = SessionFilter.class.getDeclaredField("PUBLIC_PATHS");
        f.setAccessible(true);
        return (Set<String>) f.get(null);
    }

    private static boolean isPublic(String path) throws Exception {
        java.lang.reflect.Method m = SessionFilter.class.getDeclaredMethod("isPublic", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(new SessionFilter(), path);
    }
}
