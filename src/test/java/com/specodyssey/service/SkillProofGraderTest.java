package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillProofGraderTest {

    @Test
    void gradeEntryNote_passes_whenLengthMentionsAndCodeBlockAllMet() {
        String note = ("Java".repeat(2) + " ".repeat(50)).repeat(8) + "```\nSystem.out.println(1);\n```";
        assertTrue(note.trim().length() >= 300);

        var result = SkillProofGrader.gradeEntryNote(note, "Java");

        assertEquals(SkillProofGrader.PASSED, result.status());
    }

    @Test
    void gradeEntryNote_needsRevision_whenTooShort() {
        var result = SkillProofGrader.gradeEntryNote("Java는 좋은 언어입니다. ```code```", "Java");

        assertEquals(SkillProofGrader.NEEDS_REVISION, result.status());
        assertTrue(result.note().contains("글자 수"));
    }

    @Test
    void gradeEntryNote_needsRevision_whenMissingCodeBlock() {
        String longEnoughNoCode = "Java ".repeat(80) + " Java";

        var result = SkillProofGrader.gradeEntryNote(longEnoughNoCode, "Java");

        assertEquals(SkillProofGrader.NEEDS_REVISION, result.status());
        assertTrue(result.note().contains("코드 블록"));
    }

    @Test
    void gradeExpertArticle_passes_whenLengthMentionsAndLinkAllMet() {
        String article = "Kotlin ".repeat(150) + "https://kotlinlang.org/docs";

        var result = SkillProofGrader.gradeExpertArticle(article, "Kotlin");

        assertEquals(SkillProofGrader.PASSED, result.status());
    }

    @Test
    void gradeExpertArticle_needsRevision_whenMissingExternalLink() {
        String article = "Kotlin ".repeat(150);

        var result = SkillProofGrader.gradeExpertArticle(article, "Kotlin");

        assertEquals(SkillProofGrader.NEEDS_REVISION, result.status());
        assertTrue(result.note().contains("외부 링크"));
    }

    @Test
    void gradeEntryNote_mentionCountIsCaseInsensitive() {
        String note = "JAVA is great. java is fun. ```code block``` ".repeat(8);

        var result = SkillProofGrader.gradeEntryNote(note, "Java");

        assertEquals(SkillProofGrader.PASSED, result.status());
    }
}
