package com.specodyssey.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 버전 비교 · specodyssey:// 읽기 · 업데이트 판단 */
class SmallPartsTest {

    @Test
    void 버전_비교() {
        assertTrue(Version.compare("0.2.0", "0.1.9") > 0);
        assertTrue(Version.compare("1.10.0", "1.9.3") > 0, "숫자로 비교한다 (글자 순서 아님)");
        assertEquals(0, Version.compare("companion-v1.2.0", "1.2.0"));
        assertTrue(Version.compare("v1.2", "1.2.1") < 0);
    }

    @Test
    void 웹_버튼이_연_주소에서_코드와_서버를_읽는다() {
        LaunchArgs a = LaunchArgs.parse(new String[]{
                "specodyssey://connect?code=abc_DEF-123&server=http%3A%2F%2Flocalhost%2Fspec_odyssey"});
        assertEquals("abc_DEF-123", a.code());
        assertEquals("http://localhost/spec_odyssey", a.server());
        assertTrue(a.hasConnect());
        assertEquals(a, LaunchArgs.parse(a.toLine().split(" ")), "켜진 캐릭터에게 넘겨도 같은 값");
    }

    @Test
    void 웹_주소가_아닌_서버는_받지_않는다() {
        LaunchArgs a = LaunchArgs.parse(new String[]{"specodyssey://connect?code=x&server=file%3A%2F%2FC%3A%2F"});
        assertNull(a.server());
        assertFalse(a.hasConnect());
        assertTrue(LaunchArgs.parse(new String[]{"--updated"}).updated());
    }

    @Test
    void 업데이트는_더_새_버전일_때만() {
        assertTrue(Updater.isNewer("0.2.0", "0.1.0"));
        assertFalse(Updater.isNewer("0.1.0", "0.1.0"));
        assertFalse(Updater.isNewer(null, "0.1.0"), "올라간 파일이 없으면 업데이트 없음");
        assertEquals("연습장 연동", Updater.firstLine("- 연습장 연동\n- 기타"));
    }
}
