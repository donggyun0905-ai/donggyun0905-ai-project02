package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlRulesTest {

    @Test
    void http와_https_웹_주소만_허용한다() {
        assertTrue(UrlRules.isWebUrl("https://github.com/a/b"));
        assertTrue(UrlRules.isWebUrl("http://example.com:8080/path?x=1#y"));
        assertTrue(UrlRules.isWebUrl("HTTPS://Example.com"));
        for (String bad : new String[] {"javascript:alert(1)", "data:text/html,<script>1</script>", "file:///etc/passwd",
                "ftp://example.com", "//example.com", "example.com", "https://", "http:///nohost", "not a url", "", "   ", null}) {
            assertFalse(UrlRules.isWebUrl(bad), String.valueOf(bad));
        }
    }

    @Test
    void 너무_긴_주소는_거절한다() {
        String longUrl = "https://example.com/" + "a".repeat(UrlRules.MAX_URL_LENGTH);
        assertFalse(UrlRules.isWebUrl(longUrl));
        assertEquals("배포 주소는 500자 이내로 입력해주세요.",
                assertThrows(IllegalArgumentException.class, () -> UrlRules.requireWebUrlIfPresent(longUrl, "배포 주소")).getMessage());
    }

    @Test
    void 값이_없으면_통과하고_있으면_웹_주소여야_한다() {
        assertDoesNotThrow(() -> UrlRules.requireWebUrlIfPresent(null, "저장소"));
        assertDoesNotThrow(() -> UrlRules.requireWebUrlIfPresent("https://a.com", "저장소"));
        assertEquals("저장소는 http:// 또는 https://로 시작하는 올바른 주소여야 합니다.",
                assertThrows(IllegalArgumentException.class, () -> UrlRules.requireWebUrlIfPresent("javascript:1", "저장소")).getMessage());
    }
}
