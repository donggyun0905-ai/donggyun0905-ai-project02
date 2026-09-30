package com.specodyssey.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SKILL 단계 학습 검증 — 규칙 기반 자동 판정 (팀 결정, 2026-09-30).
 * AI 채점안(비용·응답 시간)과 비교해 학생 프로젝트 규모에서는 규칙 기반으로 우선 가고,
 * AI는 나중에 끼워 넣기로 했다(관리자 검수 화면도 추후 과제). 글자 수·기술명 언급 횟수·
 * ENTRY의 코드 블록/EXPERT의 외부 링크 포함 여부만 본다 — 키워드·글자수 기준이라 의미
 * 없는 내용도 통과할 수 있다는 한계를 팀이 인지한 채로 채택했다(악용 유인이 적은 규모라 판단).
 */
public final class SkillProofGrader {

    public static final String PASSED = "PASSED";
    public static final String NEEDS_REVISION = "NEEDS_REVISION";

    private static final int ENTRY_MIN_LENGTH = 300;
    private static final int ENTRY_MIN_MENTIONS = 2;
    private static final int EXPERT_MIN_LENGTH = 800;
    private static final int EXPERT_MIN_MENTIONS = 3;
    private static final Pattern CODE_BLOCK = Pattern.compile("```");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("https?://\\S+");

    public record GradeResult(String status, String note) {
        public boolean passed() {
            return PASSED.equals(status);
        }
    }

    private SkillProofGrader() {
    }

    // ENTRY: 공부노트 — 300자 이상 + 기술명 2회 이상 + 코드 블록(```) 1개 이상
    public static GradeResult gradeEntryNote(String content, String skillName) {
        return grade(content, skillName, ENTRY_MIN_LENGTH, ENTRY_MIN_MENTIONS, true, false);
    }

    // EXPERT: 기술 설명 글 — 800자 이상 + 기술명 3회 이상 + 외부 링크 1개 이상
    public static GradeResult gradeExpertArticle(String content, String skillName) {
        return grade(content, skillName, EXPERT_MIN_LENGTH, EXPERT_MIN_MENTIONS, false, true);
    }

    private static GradeResult grade(String content, String skillName, int minLength, int minMentions,
            boolean requireCodeBlock, boolean requireExternalLink) {
        String text = content == null ? "" : content;
        int length = text.trim().length();
        int mentions = countMentions(text, skillName);
        boolean hasCodeBlock = CODE_BLOCK.matcher(text).find();
        boolean hasExternalLink = EXTERNAL_LINK.matcher(text).find();

        List<String> failed = new ArrayList<>();
        if (length < minLength) {
            failed.add("글자 수 " + length + "자 (기준 " + minLength + "자 이상)");
        }
        if (mentions < minMentions) {
            failed.add("기술명 언급 " + mentions + "회 (기준 " + minMentions + "회 이상)");
        }
        if (requireCodeBlock && !hasCodeBlock) {
            failed.add("코드 블록(```) 없음");
        }
        if (requireExternalLink && !hasExternalLink) {
            failed.add("외부 링크(http:// 또는 https://) 없음");
        }

        if (failed.isEmpty()) {
            return new GradeResult(PASSED, "자동 판정 통과 (글자 수 " + length + "자, 기술명 언급 " + mentions + "회)");
        }
        return new GradeResult(NEEDS_REVISION, "다음 기준을 채우지 못했습니다 — " + String.join(", ", failed));
    }

    private static int countMentions(String text, String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return 0;
        }
        String lowerText = text.toLowerCase();
        String lowerSkill = skillName.toLowerCase();
        int count = 0;
        int idx = 0;
        while ((idx = lowerText.indexOf(lowerSkill, idx)) != -1) {
            count++;
            idx += lowerSkill.length();
        }
        return count;
    }
}
