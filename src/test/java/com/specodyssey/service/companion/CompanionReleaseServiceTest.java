package com.specodyssey.service.companion;

import com.specodyssey.dto.CompanionReleaseDto;
import com.specodyssey.util.DBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 캐릭터 설치 파일 DB 보관 — 8MB 조각으로 나눠 저장하고 그대로 이어서 내려주는지, 버전 검사 (실제 DB) */
class CompanionReleaseServiceTest {

    private static final String VERSION = "999.0.0";
    private final CompanionReleaseService service = new CompanionReleaseService();

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement p1 = conn.prepareStatement(
                     "DELETE c FROM COMPANION_RELEASE_CHUNK c JOIN COMPANION_RELEASE r ON r.id = c.release_id WHERE r.version LIKE '999.%'");
             PreparedStatement p2 = conn.prepareStatement("DELETE FROM COMPANION_RELEASE WHERE version LIKE '999.%'")) {
            p1.executeUpdate();
            p2.executeUpdate();
        }
    }

    @Test
    void 여러_조각으로_나눠_저장하고_그대로_이어서_내려준다() throws Exception {
        byte[] file = new byte[CompanionReleaseService.CHUNK_BYTES * 2 + 12345]; // 조각 3개
        new Random(7).nextBytes(file);
        CompanionReleaseDto saved = service.upload(null, "v" + VERSION, "  바뀐 점  ", "x.exe", new ByteArrayInputStream(file));

        assertEquals(VERSION, saved.getVersion(), "앞의 v는 떼고 저장");
        assertEquals(file.length, saved.getFileSize());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file)), saved.getSha256());
        assertEquals(VERSION, service.latest().getVersion());
        assertEquals("바뀐 점", service.latest().getNotes());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeTo(service.latest(), out);
        assertArrayEquals(file, out.toByteArray());
    }

    @Test
    void 버전_형식_중복_낮은_버전은_받지_않는다() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(null, "abc", null, "x.exe", new ByteArrayInputStream(new byte[10])));
        service.upload(null, VERSION, null, "x.exe", new ByteArrayInputStream(new byte[10]));
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(null, VERSION, null, "x.exe", new ByteArrayInputStream(new byte[10])), "같은 버전");
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(null, "998.9.9", null, "x.exe", new ByteArrayInputStream(new byte[10])), "최신보다 낮음");
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(null, "999.0.1", null, "x.exe", new ByteArrayInputStream(new byte[0])), "빈 파일");
    }

    @Test
    void 버전_비교는_숫자로() {
        assertTrue(CompanionReleaseService.compare("0.10.0", "0.9.9") > 0);
        assertEquals(0, CompanionReleaseService.compare("1.2.3", "1.2.3"));
    }
}
