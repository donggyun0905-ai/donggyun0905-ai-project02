package com.specodyssey.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 버전 비교 · specodyssey:// 읽기 · 업데이트 릴리스 고르기 */
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
    void 업데이트는_캐릭터_릴리스_중_더_새것만() {
        String json = """
                [
                  {"tag_name":"v9.9.9","draft":false,"prerelease":false,"assets":[]},
                  {"tag_name":"companion-v0.3.0","draft":false,"prerelease":true,"assets":[
                    {"name":"SpecOdysseyCompanion.zip","browser_download_url":"https://x/z3"},
                    {"name":"SpecOdysseyCompanion.zip.sha256","browser_download_url":"https://x/s3"}]},
                  {"tag_name":"companion-v0.2.0","draft":false,"prerelease":false,"body":"- 연습장 연동\\n- 기타","assets":[
                    {"name":"SpecOdysseyCompanion.zip","browser_download_url":"https://x/z2"},
                    {"name":"SpecOdysseyCompanion.zip.sha256","browser_download_url":"https://x/s2"}]},
                  {"tag_name":"companion-v0.1.5","draft":false,"prerelease":false,"assets":[
                    {"name":"SpecOdysseyCompanion.zip","browser_download_url":"https://x/z15"}]}
                ]""";
        JsonArray releases = new Gson().fromJson(json, JsonArray.class);
        Updater.Release r = Updater.pickNewer(releases, "0.1.0");
        assertEquals("0.2.0", r.version(), "미리보기·확인값 없는 릴리스·다른 태그는 건너뛴다");
        assertEquals("https://x/z2", r.zipUrl());
        assertEquals("연습장 연동", r.notes());
        assertNull(Updater.pickNewer(releases, "0.2.0"), "같은 버전이면 업데이트 없음");
    }
}
