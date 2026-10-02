package com.specodyssey.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCodeTest {

    @Test
    void 새_코드는_4자씩_끊은_16자이고_헷갈리는_글자가_없다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = RecoveryCode.generate();
            assertTrue(code.matches("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}"), code);
            assertFalse(code.matches(".*[01OI].*"), code);
            assertTrue(RecoveryCode.looksValid(code));
            seen.add(code);
        }
        assertEquals(200, seen.size(), "무작위라 겹치지 않는다");
    }

    @Test
    void 입력은_대소문자_하이픈_공백을_무시하고_비교용으로_다듬는다() {
        assertEquals("ABCD2345EFGH6789", RecoveryCode.forHash(" abcd-2345 efgh-6789 "));
        assertTrue(RecoveryCode.looksValid("abcd-2345-efgh-6789"));
        assertEquals("", RecoveryCode.normalize(null));
    }

    @Test
    void 길이나_글자가_틀리면_형식부터_거절한다() {
        assertFalse(RecoveryCode.looksValid(null));
        assertFalse(RecoveryCode.looksValid(""));
        assertFalse(RecoveryCode.looksValid("ABCD-2345-EFGH"));
        assertFalse(RecoveryCode.looksValid("ABCD-2345-EFGH-6780")); // 0은 쓰지 않는 글자
        assertFalse(RecoveryCode.looksValid("ABCD-2345-EFGH-67890"));
    }
}
