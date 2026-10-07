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

    @Test
    void 저장은_디스크에_쓰지_않고_내용과_체크섬을_돌려준다() throws Exception {
        byte[] body = "이력서 본문".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileStorageUtil.SavedFile saved = FileStorageUtil.save(new java.io.ByteArrayInputStream(body), "내 이력서.pdf");

        assertTrue(java.util.Arrays.equals(body, saved.getData()));
        assertEquals(body.length, saved.getFileSize());
        assertEquals(64, saved.getChecksum().length()); // SHA-256 hex
        assertTrue(saved.getStoredName().endsWith(".pdf"));
        assertEquals(null, saved.getFilePath()); // 새 서류는 경로가 없다 — DB(file_data)에만 있다
        assertFalse(FileStorageUtil.existsOnDisk(saved.getFilePath()));
        FileStorageUtil.deleteQuietly(null); // 경로 없는 서류를 정리해도 예외가 나지 않는다
    }

    @Test
    void 화면_미리보기는_PDF만_대소문자_무관하게_허용한다() {
        assertTrue(FileStorageUtil.isPdf("이력서.pdf"));
        assertTrue(FileStorageUtil.isPdf("resume.PDF"));
        assertFalse(FileStorageUtil.isPdf("resume.docx"));
        assertFalse(FileStorageUtil.isPdf("resume.hwp"));
        assertFalse(FileStorageUtil.isPdf("pdf"));
        assertFalse(FileStorageUtil.isPdf(null));
    }
}
