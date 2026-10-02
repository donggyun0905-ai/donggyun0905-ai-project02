package com.specodyssey.service.archive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSP가 읽는 record는 Tomcat 10.1은 getX()/isX(), Tomcat 11은 x()만 찾는다. 계산해서 만든 값(record 구성요소가 아닌 것)은
 * 둘 다 있어야 한다 — 없으면 글 상세 화면이 Tomcat 11에서만 500이 난다(실제로 겪음).
 */
class ArchiveViewRecordElTest {

    @Test
    void 본문_조각의_계산된_값은_getter와_x_형태_모두_같은_값을_준다() {
        ArchiveContentCodec.Segment code = new ArchiveContentCodec.Segment(null, null, "java", "int a;");
        assertTrue(code.isCodeBlock());
        assertEquals(code.isCodeBlock(), code.codeBlock());
        assertEquals(code.getCodeLanguageLabel(), code.codeLanguageLabel());
        assertFalse(code.media());

        ArchiveContentCodec.Segment text = new ArchiveContentCodec.Segment("글", null, null, null);
        assertFalse(text.codeBlock());
        assertEquals(text.isMedia(), text.media());
    }

    @Test
    void 목록_페이지의_이전_다음_여부도_두_형태가_같다() {
        SpecArchiveService.ListPage page = new SpecArchiveService.ListPage(List.of(), 2, 3, 25);
        assertTrue(page.hasPrev() && page.isHasPrev());
        assertTrue(page.hasNext() && page.isHasNext());
        SpecArchiveService.ListPage last = new SpecArchiveService.ListPage(List.of(), 3, 3, 25);
        assertFalse(last.hasNext() || last.isHasNext());
    }
}
