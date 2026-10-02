package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 업로드 허용 목록·MIME 고정 — 서류 보관함, 로드맵 제출, 공유 문서가 모두 이 한 곳을 쓴다. */
class FileStorageUtilTest {

    @Test
    void 허용_목록에_있는_형식만_올릴_수_있다() {
        for (String ok : new String[] {"a.pdf", "A.PDF", "a.docx", "a.hwp", "a.hwpx", "a.pptx", "a.txt", "README.md",
                "a.png", "a.jpg", "a.JPEG", "a.gif", "a.zip", "내 이력서.final.pdf"}) {
            assertTrue(FileStorageUtil.isAllowedFile(ok), ok);
        }
        // 예전 차단 목록(exe 등)에 없어서 통과하던 형식들 — 브라우저가 열면 스크립트가 돌 수 있는 것들
        for (String bad : new String[] {"a.html", "a.htm", "a.svg", "a.js", "a.php", "a.jsp", "a.xml", "a.exe", "a.sh",
                "noext", "a.", ".pdf.exe", "", null}) {
            assertFalse(FileStorageUtil.isAllowedFile(bad), String.valueOf(bad));
        }
    }

    @Test
    void MIME은_확장자로_정하고_모르는_형식은_octet_stream이다() {
        assertEquals("application/pdf", FileStorageUtil.mimeTypeFor("x.PDF"));
        assertEquals("image/png", FileStorageUtil.mimeTypeFor("x.png"));
        assertEquals("text/plain; charset=UTF-8", FileStorageUtil.mimeTypeFor("README.md"));
        // 예전에 html로 올려 DB에 text/html이 남아 있어도, 서빙할 때는 이름의 확장자로만 정한다
        assertEquals("application/octet-stream", FileStorageUtil.mimeTypeFor("evil.html"));
        assertEquals("application/octet-stream", FileStorageUtil.mimeTypeFor(null));
    }

    @Test
    void 브라우저에서_바로_열어도_안전한_것은_PDF와_이미지뿐이다() {
        assertTrue(FileStorageUtil.isInlineSafe("a.pdf"));
        assertTrue(FileStorageUtil.isInlineSafe("a.PNG"));
        assertFalse(FileStorageUtil.isInlineSafe("a.txt"));
        assertFalse(FileStorageUtil.isInlineSafe("a.zip"));
        assertFalse(FileStorageUtil.isInlineSafe("a.html"));
        assertFalse(FileStorageUtil.isInlineSafe(null));
    }
}
