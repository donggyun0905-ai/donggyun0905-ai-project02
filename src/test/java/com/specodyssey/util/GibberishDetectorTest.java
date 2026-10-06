package com.specodyssey.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GibberishDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {"sdafdsafdsa", "asdfasdf", "qwerty", "xkcdqz", "ㅁㄴㅇㄹ", "Docker ㅋㅋ", "1234", "!!!!",
            "zzzz", "아아아아", "asdasd", "sDfGhJkL"})
    void 키보드를_아무렇게나_두드린_입력은_엉터리다(String input) {
        assertTrue(GibberishDetector.looksLikeGibberish(input), input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "LightGBM", "PyTorch", "MSSQL", "HTTPS", "Nginx", "Svelte", "gRPC", "Next.js 14",
            "scikit-learn", "Elasticsearch", "Prometheus", "Spring Boot 3", "자바 백엔드", "정보처리기사", "TOEIC 900",
            "JLPT N1", "OPIc IH", "교내 해커톤 대상", "2024 공개SW 개발자대회 은상", "C++", "R", "Go"})
    void 실제_기술_자격증_수상_이름은_통과한다(String input) {
        assertFalse(GibberishDetector.looksLikeGibberish(input), input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 빈_입력은_판단하지_않는다(String input) {
        assertFalse(GibberishDetector.looksLikeGibberish(input));
    }
}
