package com.specodyssey.service;

import com.specodyssey.util.ExternalApiClient.ExternalApiException;
import com.specodyssey.util.Judge0Client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 제출 코드가 "정상적으로 컴파일되는지"만 확인한다 — 정답 채점은 하지 않는다.
 * 관련 요구사항: FR-53 (일일 미션 "정답 입력하기")
 *
 * 프로그래머스 풀이는 solution 함수만 있고 main이 없어서, 언어별로 컴파일만 되게 손질해서 Judge0에 보낸다.
 *   Java·C·C++  : 컴파일 에러(status 6)만 실패로 본다. 실행 단계 오류(main 없음 등)는 무시한다.
 *   Python·JS   : 사용자 코드를 실행하지 않고 문법만 검사하는 래퍼로 감싸 보낸다.
 *   SQL(SQLite) : 테이블이 없어 "no such table" 같은 오류는 당연히 나므로 문법 오류만 실패로 본다.
 */
public class CodeCompileService {

    /** 제출 가능한 언어. id는 Judge0 CE language_id. */
    public enum Language {
        JAVA("Java", 62),
        PYTHON("Python 3", 71),
        CPP("C++", 54),
        C("C", 50),
        JAVASCRIPT("JavaScript", 63),
        SQL("SQL", 82);

        private final String label;
        private final int judge0Id;

        Language(String label, int judge0Id) {
            this.label = label;
            this.judge0Id = judge0Id;
        }

        public String getCode() {
            return name();
        }

        public String getLabel() {
            return label;
        }

        /** 모르는 값이면 null. */
        public static Language from(String code) {
            if (code == null) {
                return null;
            }
            for (Language l : values()) {
                if (l.name().equalsIgnoreCase(code.trim())) {
                    return l;
                }
            }
            return null;
        }
    }

    /** 확인 결과. passed=false면 message에 컴파일러가 알려 준 오류가 들어 있다. */
    public record CompileCheck(boolean passed, String message) {
    }

    // Judge0 status id
    private static final int STATUS_ACCEPTED = 3;
    private static final int STATUS_COMPILATION_ERROR = 6;
    private static final int STATUS_INTERNAL_ERROR = 13;
    private static final int STATUS_EXEC_FORMAT_ERROR = 14;

    private static final int MAX_MESSAGE_LENGTH = 2000;

    // 파일명이 Main.java로 고정이라 public class Solution 은 컴파일 에러가 난다 — Main이 아닌 최상위 public 타입의 public을 뗀다
    private static final Pattern JAVA_PUBLIC_TYPE =
            Pattern.compile("(?m)^(\\s*)public\\s+((?:final\\s+|abstract\\s+)*)(class|interface|enum|record)\\s+(?!Main\\b)");
    private static final Pattern C_MAIN = Pattern.compile("\\bmain\\s*\\(");
    private static final Pattern SQL_SYNTAX_ERROR =
            Pattern.compile("(?i)(syntax error|unrecognized token|incomplete input)");

    /** 외부 API가 안 되면 ExternalApiException — 호출부가 "잠시 후 다시" 안내로 바꾼다. */
    public CompileCheck check(Language language, String code) throws ExternalApiException {
        Judge0Client.Result result = Judge0Client.submit(language.judge0Id, prepareSource(language, code));
        return interpret(language, result);
    }

    static String prepareSource(Language language, String code) {
        return switch (language) {
            case JAVA -> JAVA_PUBLIC_TYPE.matcher(code).replaceAll("$1$2$3 ");
            case C, CPP -> C_MAIN.matcher(code).find() ? code : code + "\n\nint main(void) { return 0; }\n";
            case PYTHON -> "import base64, sys\n"
                    + "src = base64.b64decode(\"" + base64(code) + "\").decode(\"utf-8\")\n"
                    + "try:\n"
                    + "    compile(src, \"solution.py\", \"exec\")\n"
                    + "except SyntaxError as e:\n"
                    + "    sys.stderr.write(\"%s: %s (line %s)\\n\" % (type(e).__name__, e.msg, e.lineno))\n"
                    + "    sys.exit(1)\n"
                    + "print(\"OK\")\n";
            case JAVASCRIPT -> "const vm = require('vm');\n"
                    + "const src = Buffer.from('" + base64(code) + "', 'base64').toString('utf8');\n"
                    + "try {\n"
                    + "  new vm.Script(src, { filename: 'solution.js' });\n"
                    + "  console.log('OK');\n"
                    + "} catch (e) {\n"
                    + "  console.error(e.name + ': ' + e.message);\n"
                    + "  process.exit(1);\n"
                    + "}\n";
            case SQL -> code;
        };
    }

    static CompileCheck interpret(Language language, Judge0Client.Result r) throws ExternalApiException {
        int status = r.statusId();
        if (status == STATUS_INTERNAL_ERROR || status == STATUS_EXEC_FORMAT_ERROR || status < 0) {
            throw new ExternalApiException("컴파일 서버 오류: " + r.statusDescription(), null);
        }
        switch (language) {
            case JAVA, C, CPP -> {
                return status == STATUS_COMPILATION_ERROR
                        ? new CompileCheck(false, trim(firstNonBlank(r.compileOutput(), r.message())))
                        : new CompileCheck(true, null);
            }
            case PYTHON, JAVASCRIPT -> {
                if (status == STATUS_ACCEPTED) {
                    return new CompileCheck(true, null);
                }
                String err = firstNonBlank(r.stderr(), r.message());
                if (err == null) {
                    // 문법 검사 래퍼가 오류 메시지 없이 끝났다면 코드 문제가 아니라 실행 환경 문제다
                    throw new ExternalApiException("컴파일 확인 실패: " + r.statusDescription(), null);
                }
                return new CompileCheck(false, trim(err));
            }
            case SQL -> {
                String out = String.join("\n", nz(r.stderr()), nz(r.stdout()), nz(r.message()));
                return SQL_SYNTAX_ERROR.matcher(out).find()
                        ? new CompileCheck(false, trim(firstNonBlank(r.stderr(), out)))
                        : new CompileCheck(true, null);
            }
            default -> throw new IllegalStateException("지원하지 않는 언어: " + language);
        }
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s) {
        if (s == null) {
            return "컴파일 오류가 발생했습니다.";
        }
        s = s.strip();
        return s.length() > MAX_MESSAGE_LENGTH ? s.substring(0, MAX_MESSAGE_LENGTH) + "\n…(생략)" : s;
    }
}
