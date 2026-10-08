package com.specodyssey.controller;

import com.specodyssey.service.companion.CompanionReleaseService;
import com.specodyssey.util.DBUtil;
import com.specodyssey.web.FakeWeb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 설치 파일을 실제로 받아간 뒤에만 메뉴가 [캐릭터 켜기]로 바뀌는지 (2026-10-08).
 * 받기 전에 바꿔 두면 설치가 안 된 사람이 켜기를 눌러 아무 일도 안 일어난다 — 그게 이 테스트의 요지다.
 */
class CompanionDownloadMarksSessionTest {

    private final CompanionServlet servlet = new CompanionServlet();
    private final CompanionReleaseService releaseService = new CompanionReleaseService();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p1 = conn.prepareStatement(
                     "DELETE c FROM COMPANION_RELEASE_CHUNK c JOIN COMPANION_RELEASE r ON r.id = c.release_id"
                             + " WHERE r.version LIKE '997.%'");
             PreparedStatement p2 = conn.prepareStatement("DELETE FROM COMPANION_RELEASE WHERE version LIKE '997.%'")) {
            p1.executeUpdate();
            p2.executeUpdate();
        }
    }

    @Test
    @DisplayName("파일을 다 받아가면 세션에 표시가 남는다")
    void 받아가면_세션에_표시가_남는다() throws Exception {
        byte[] file = "setup-exe-내용".getBytes("UTF-8");
        releaseService.upload(null, "997.0.0", null, "x.exe", new ByteArrayInputStream(file));

        FakeWeb.Request req = FakeWeb.request();
        req.pathInfo = "/download";
        req.session = new FakeWeb.Session();
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doGet(req.http(), resp.http());

        assertArrayEquals(file, resp.body.toByteArray(), "파일이 그대로 내려가야 한다");
        assertEquals(Boolean.TRUE, req.session.attributes.get(CompanionServlet.DOWNLOADED_ATTR));
    }

    @Test
    @DisplayName("올라간 파일이 없으면 404이고 표시도 남지 않는다 — 켜기로 바뀌면 안 된다")
    void 받을_파일이_없으면_표시도_없다() throws Exception {
        if (releaseService.latest() != null) {
            return; // 공유 DB에 누군가 올려 둔 버전이 있으면 이 상황을 만들 수 없다
        }
        FakeWeb.Request req = FakeWeb.request();
        req.pathInfo = "/download";
        req.session = new FakeWeb.Session();
        FakeWeb.Response resp = FakeWeb.response();

        servlet.doGet(req.http(), resp.http());

        assertEquals(404, resp.errorStatus);
        assertNull(req.session.attributes.get(CompanionServlet.DOWNLOADED_ATTR));
    }
}
