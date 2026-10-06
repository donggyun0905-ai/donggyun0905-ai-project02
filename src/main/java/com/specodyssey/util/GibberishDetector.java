package com.specodyssey.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 키보드를 아무렇게나 두드린 입력("sdafdsafdsa", "ㅁㄴㅇㄹ", "1234")인지 글자 모양만 보고 판단한다.
 * 프로필의 기술·스펙 이름 검사(ProfileInputChecker)에서 쓴다 (2026-10-06).
 *
 * 임베딩은 엉터리 입력에도 가장 가까운 기술을 0.6 안팎으로 돌려줘서 "말이 되는 글자인가"는 가르지 못한다 —
 * 그래서 이 판단은 규칙으로 하고, 임베딩은 고칠 후보를 찾는 데만 쓴다.
 * 짧은 약어(SQL·AWS·npm)나 대문자 약어를 막지 않도록 규칙은 5글자 이상의 영어 단어에만 적용한다.
 * 이미 아는 기술·자격증 이름인지는 부르는 쪽이 먼저 확인한다.
 */
public final class GibberishDetector {

    // 홑자모(ㄱ~ㅎ, ㅏ~ㅣ) — 완성되지 않은 한글은 이름에 들어가지 않는다
    private static final Pattern JAMO = Pattern.compile("[\\u3131-\\u318E]");
    private static final Pattern LETTER = Pattern.compile("[\\p{L}]");
    // 같은 글자 4번 이상 연속 ("aaaa", "아아아아")
    private static final Pattern SAME_CHAR_RUN = Pattern.compile("(\\p{L})\\1{3,}");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^A-Za-z]+");
    // 대소문자 경계 — "LightGBM"은 Light + GBM, "PyTorch"는 Py + Torch로 보고 모음 규칙을 적용한다
    private static final Pattern CASE_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern REPEATED_CHUNK = Pattern.compile("^(.{2,})\\1+$");
    private static final String VOWELS = "aeiouy";
    private static final String[] KEYBOARD_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

    private static final int MIN_WORD_LENGTH = 5;
    private static final int MAX_CONSONANT_RUN = 4;
    private static final int MIN_ROW_LENGTH = 6;
    private static final double MAX_DISTINCT_RATIO = 0.4;

    private GibberishDetector() {
    }

    public static boolean looksLikeGibberish(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (!LETTER.matcher(trimmed).find() || JAMO.matcher(trimmed).find()
                || SAME_CHAR_RUN.matcher(trimmed.toLowerCase()).find()) {
            return true;
        }
        for (String token : TOKEN_SPLIT.split(trimmed)) {
            if (isGibberishWord(token)) {
                return true;
            }
        }
        return false;
    }

    // 영어 단어 하나
    private static boolean isGibberishWord(String token) {
        if (token.length() < MIN_WORD_LENGTH) {
            return false;
        }
        String word = token.toLowerCase();
        if (REPEATED_CHUNK.matcher(word).matches() || allInOneKeyboardRow(word) || distinctRatio(word) <= MAX_DISTINCT_RATIO) {
            return true;
        }
        for (String part : CASE_BOUNDARY.split(token)) {
            // 대문자 약어(MSSQL·HTTPS·GBM)는 모음 없이도 쓴다
            if (part.length() >= MIN_WORD_LENGTH && !part.equals(part.toUpperCase())) {
                String lower = part.toLowerCase();
                if (countVowels(lower) == 0 || longestConsonantRun(lower) > MAX_CONSONANT_RUN) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allInOneKeyboardRow(String word) {
        if (word.length() < MIN_ROW_LENGTH) {
            return false;
        }
        for (String row : KEYBOARD_ROWS) {
            if (word.chars().allMatch(c -> row.indexOf(c) >= 0)) {
                return true;
            }
        }
        return false;
    }

    // "sdafdsafdsa"처럼 몇 글자만 돌려 쓴 입력 — 6글자 이상에서만 본다
    private static double distinctRatio(String word) {
        if (word.length() < MIN_ROW_LENGTH) {
            return 1.0;
        }
        Set<Character> distinct = new HashSet<>();
        for (char c : word.toCharArray()) {
            distinct.add(c);
        }
        return (double) distinct.size() / word.length();
    }

    private static int countVowels(String word) {
        return (int) word.chars().filter(c -> VOWELS.indexOf(c) >= 0).count();
    }

    private static int longestConsonantRun(String word) {
        int longest = 0;
        int run = 0;
        for (char c : word.toCharArray()) {
            run = VOWELS.indexOf(c) >= 0 ? 0 : run + 1;
            longest = Math.max(longest, run);
        }
        return longest;
    }
}
