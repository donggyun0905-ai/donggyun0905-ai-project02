package com.specodyssey.service;

import com.specodyssey.service.CodeCompileService.CompileCheck;
import com.specodyssey.service.CodeCompileService.Language;
import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.Judge0Client;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeCompileService의 소스 손질·결과 해석 규칙 단위테스트 (Judge0·DB 호출 없음).
 */
class CodeCompileServiceTest {

    private static Judge0Client.Result result(int status, String stdout, String stderr, String compileOutput) {
        return new Judge0Client.Result("t", status, "desc", stdout, stderr, compileOutput, null);
    }

    @Test
    void 자바_public_class_Solution은_public을_뗀다() {
        String src = "import java.util.*;\npublic class Solution {\n    public class Inner {}\n}\n";
        String prepared = CodeCompileService.prepareSource(Language.JAVA, src);
        assertTrue(prepared.contains("\nclass Solution {"));
        assertFalse(prepared.contains("public class"));
    }

    @Test
    void 자바_public_class_Main은_그대로() {
        String src = "public class Main { public static void main(String[] a) {} }";
        assertEquals(src, CodeCompileService.prepareSource(Language.JAVA, src));
    }

    @Test
    void C_main이_없으면_빈_main을_붙인다() {
        String prepared = CodeCompileService.prepareSource(Language.CPP, "int solution(int n) { return n; }");
        assertTrue(prepared.contains("int main(void)"));
        String withMain = "int main() { return 0; }";
        assertEquals(withMain, CodeCompileService.prepareSource(Language.C, withMain));
    }

    @Test
    void 파이썬과_JS는_코드를_실행하지_않는_문법검사_래퍼로_감싼다() {
        String code = "def solution(n):\n    return n\n";
        String py = CodeCompileService.prepareSource(Language.PYTHON, code);
        assertTrue(py.contains("compile(src"));
        assertFalse(py.contains("def solution"), "원본 코드는 base64로만 들어가야 한다");

        String js = CodeCompileService.prepareSource(Language.JAVASCRIPT, "function solution(n) { return n; }");
        assertTrue(js.contains("new vm.Script"));
        assertFalse(js.contains("function solution"));
    }

    @Test
    void 컴파일_언어는_컴파일_에러만_실패() throws ExternalApiException {
        CompileCheck fail = CodeCompileService.interpret(Language.JAVA,
                result(6, null, null, "Main.java:3: error: ';' expected"));
        assertFalse(fail.passed());
        assertTrue(fail.message().contains("';' expected"));

        // main이 없어 실행 단계에서 난 오류(NZEC)는 컴파일 통과로 본다
        assertTrue(CodeCompileService.interpret(Language.JAVA,
                result(11, null, "Could not find or load main class Main", null)).passed());
        assertTrue(CodeCompileService.interpret(Language.CPP, result(3, "", null, null)).passed());
    }

    @Test
    void 파이썬_문법오류는_실패_통과는_성공() throws ExternalApiException {
        assertTrue(CodeCompileService.interpret(Language.PYTHON, result(3, "OK\n", null, null)).passed());
        CompileCheck fail = CodeCompileService.interpret(Language.PYTHON,
                result(11, null, "SyntaxError: invalid syntax (line 2)\n", null));
        assertFalse(fail.passed());
        assertTrue(fail.message().startsWith("SyntaxError"));
    }

    @Test
    void SQL은_문법오류만_실패() throws ExternalApiException {
        assertTrue(CodeCompileService.interpret(Language.SQL,
                result(3, null, "Error: near line 1: no such table: ANIMAL_INS\n", null)).passed());
        assertTrue(CodeCompileService.interpret(Language.SQL,
                result(11, null, "Error: near line 1: no such function: DATE_FORMAT\n", null)).passed());
        assertFalse(CodeCompileService.interpret(Language.SQL,
                result(11, null, "Error: near line 1: near \"SELEC\": syntax error\n", null)).passed());
    }

    @Test
    void 컴파일_서버_오류는_예외로_알린다() {
        assertThrows(ExternalApiException.class,
                () -> CodeCompileService.interpret(Language.JAVA, result(13, null, null, null)));
        assertThrows(ExternalApiException.class,
                () -> CodeCompileService.interpret(Language.PYTHON, result(11, null, null, null)));
    }

    @Test
    void 언어_코드_파싱() {
        assertEquals(Language.CPP, Language.from("cpp"));
        assertEquals(Language.SQL, Language.from(" SQL "));
        assertNull(Language.from("RUBY"));
        assertNull(Language.from(null));
    }
}
