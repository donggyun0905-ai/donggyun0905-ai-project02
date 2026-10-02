package com.specodyssey.service.archive;

import com.specodyssey.dto.TechArticleCommentDto;
import com.specodyssey.service.archive.SpecArchiveService.CommentThread;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 스펙 아카이브 입력 규칙 · 댓글 줄기 묶기 단위테스트 (DB 없이 실행) */
class SpecArchiveRulesTest {

    // ---------------------------------------------------------------- 글자 수

    @Test
    void 제목은_30자까지_본문은_2000자까지_댓글은_300자까지() {
        assertEquals("합격 루틴", SpecArchiveRules.checkTitle("  합격 루틴  "));
        assertDoesNotThrow(() -> SpecArchiveRules.checkTitle("가".repeat(30)));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkTitle("가".repeat(31)));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkTitle("   "));

        assertDoesNotThrow(() -> SpecArchiveRules.checkContent(ArchiveContentCodec.encode("가".repeat(2000), Set.of())));
        assertThrows(IllegalArgumentException.class,
                () -> SpecArchiveRules.checkContent(ArchiveContentCodec.encode("가".repeat(2001), Set.of())));
        assertThrows(IllegalArgumentException.class,
                () -> SpecArchiveRules.checkContent(ArchiveContentCodec.encode("  ", Set.of())));

        assertDoesNotThrow(() -> SpecArchiveRules.checkComment("가".repeat(300)));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkComment("가".repeat(301)));
    }

    @Test
    void 이모지는_한_글자로_센다() {
        assertDoesNotThrow(() -> SpecArchiveRules.checkTitle("🔥".repeat(30)), "이모지 30개는 30자");
    }

    @Test
    void 줄바꿈은_LF로_맞춘다() {
        assertEquals("첫 줄\n둘째 줄", ArchiveContentCodec.encode("첫 줄\r\n둘째 줄\r\n", Set.of()).content());
    }

    // ---------------------------------------------------------------- 유튜브

    @Test
    void 여러_형태의_유튜브_링크에서_영상_ID를_꺼낸다() {
        String id = "dQw4w9WgXcQ";
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://www.youtube.com/watch?v=" + id));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://www.youtube.com/watch?feature=share&v=" + id + "&t=30s"));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://youtu.be/" + id + "?si=abc"));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://m.youtube.com/watch?v=" + id));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://www.youtube.com/shorts/" + id));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("https://www.youtube.com/embed/" + id));
        assertEquals(id, SpecArchiveRules.parseYoutubeId("http://youtube.com/live/" + id));
    }

    @Test
    void 유튜브가_아니거나_ID가_이상하면_거부한다() {
        assertNull(SpecArchiveRules.parseYoutubeId("https://evil.com/watch?v=dQw4w9WgXcQ"));
        assertNull(SpecArchiveRules.parseYoutubeId("https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ"), "도메인 끝이 youtube.com이어야 한다");
        assertNull(SpecArchiveRules.parseYoutubeId("https://www.youtube.com/watch?v=short"));
        assertNull(SpecArchiveRules.parseYoutubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ\"><script>"));
        assertNull(SpecArchiveRules.parseYoutubeId("javascript:alert(1)//youtu.be/dQw4w9WgXcQ"));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkYoutubeUrl("https://vimeo.com/123"));
    }

    // ---------------------------------------------------------------- 이미지 링크

    @Test
    void 이미지_링크는_https만_받는다() {
        assertEquals("https://i.imgur.com/a.png", SpecArchiveRules.checkImageUrl("  https://i.imgur.com/a.png "));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkImageUrl("http://i.imgur.com/a.png"));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkImageUrl("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkImageUrl("data:image/png;base64,AAAA"));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkImageUrl("https://user:pw@example.com/a.png"));
        assertThrows(IllegalArgumentException.class, () -> SpecArchiveRules.checkImageUrl("https://example.com/" + "a".repeat(2100)));
    }

    // ---------------------------------------------------------------- 업로드 이미지 형식

    @Test
    void 파일_앞부분으로_실제_이미지_형식을_판별한다() {
        assertEquals("image/png", SpecArchiveRules.detectImageMime(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)));
        assertEquals("image/jpeg", SpecArchiveRules.detectImageMime(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertEquals("image/gif", SpecArchiveRules.detectImageMime(bytes('G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0)));
        assertEquals("image/webp", SpecArchiveRules.detectImageMime(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')));
    }

    @Test
    void 이미지가_아닌_파일은_확장자가_png여도_거부한다() {
        assertNull(SpecArchiveRules.detectImageMime("<svg onload=alert(1)>".getBytes()), "SVG는 받지 않는다");
        assertNull(SpecArchiveRules.detectImageMime("<html><script>".getBytes()));
        assertNull(SpecArchiveRules.detectImageMime(bytes('P', 'K', 3, 4, 0, 0, 0, 0, 0, 0, 0, 0)), "zip");
        assertNull(SpecArchiveRules.detectImageMime(new byte[2]));
        assertNull(SpecArchiveRules.detectImageMime(null));
        assertFalse(SpecArchiveRules.isServableImageMime("image/svg+xml"));
        assertFalse(SpecArchiveRules.isServableImageMime("text/html"));
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    // ---------------------------------------------------------------- 글쓰기 권한 티어 (점수 기준)

    private static com.specodyssey.dto.LevelTierDto tier(long id, int min, Integer max, String title) {
        com.specodyssey.dto.LevelTierDto t = new com.specodyssey.dto.LevelTierDto();
        t.setId(id);
        t.setMinScore(min);
        t.setMaxScore(max);
        t.setTitleName(title);
        return t;
    }

    @Test
    void 점수로_티어를_정하고_위의_두_티어만_글쓰기() {
        List<com.specodyssey.dto.LevelTierDto> tiers = List.of(
                tier(53, 0, 499, "첫걸음"), tier(54, 500, 1499, "방랑자"), tier(55, 1500, 2999, "항해자"),
                tier(56, 3000, 4999, "개척자"), tier(57, 5000, null, "오디세이아"));

        assertEquals(List.of("개척자", "오디세이아"),
                SpecArchiveService.topTiers(tiers).stream().map(com.specodyssey.dto.LevelTierDto::getTitleName).toList());
        assertEquals("항해자", SpecArchiveService.tierForScore(tiers, 2999).getTitleName());
        assertEquals("개척자", SpecArchiveService.tierForScore(tiers, 3000).getTitleName());
        assertEquals("오디세이아", SpecArchiveService.tierForScore(tiers, 99999).getTitleName(), "max_score NULL = 끝 없음");
        assertEquals("첫걸음", SpecArchiveService.tierForScore(tiers, 0).getTitleName());
    }

    // ---------------------------------------------------------------- 댓글 줄기 (인스타그램식)

    private static TechArticleCommentDto comment(long id, Long parent, boolean deleted) {
        TechArticleCommentDto c = new TechArticleCommentDto();
        c.setId(id);
        c.setParentCommentId(parent);
        c.setDeleted(deleted);
        return c;
    }

    @Test
    void 답글은_최상위_댓글_밑에_모인다() {
        List<CommentThread> threads = SpecArchiveService.threads(List.of(
                comment(1, null, false), comment(2, null, false), comment(3, 1L, false), comment(4, 1L, false)));

        assertEquals(2, threads.size());
        assertEquals(List.of(3L, 4L), threads.get(0).replies().stream().map(TechArticleCommentDto::getId).toList());
        assertTrue(threads.get(1).replies().isEmpty());
    }

    @Test
    void 지운_댓글은_답글이_있을_때만_자리를_남긴다() {
        List<CommentThread> threads = SpecArchiveService.threads(List.of(
                comment(1, null, true), comment(2, 1L, false),   // 지웠지만 답글 있음 → "삭제된 댓글"로 남김
                comment(3, null, true),                          // 지웠고 답글 없음 → 숨김
                comment(4, null, true), comment(5, 4L, true)));  // 답글도 다 지움 → 숨김

        assertEquals(1, threads.size());
        assertEquals(1L, threads.get(0).comment().getId());
        assertTrue(threads.get(0).comment().isDeleted());
        assertEquals(List.of(2L), threads.get(0).replies().stream().map(TechArticleCommentDto::getId).toList());
    }
}
