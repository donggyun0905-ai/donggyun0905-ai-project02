package com.specodyssey.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 직무 요구 수준(JOB_REQUIRED_SKILL.required_level)은 로드맵 티어와 같은 4단계 어휘만 쓴다 (2026-10-14).
 *
 * 왜 검사하는가 — 예전에는 요구 수준이 BASIC·INTERMEDIATE·ADVANCED 3단계로 따로 놀았다.
 * 둘 다 ADVANCED 를 쓰는데 뜻이 달라서 히트맵의 "심화"와 로드맵의 "심화"가 같은 것으로 읽혔다.
 * sql/40 에서 값을 옮겼는데, 시드 파일에 옛 어휘가 한 줄이라도 남으면 새로 만든 DB 만
 * 조용히 옛 값을 갖게 된다 — 화면에서는 "미지정" 열로 보이고, 왜 그런지 찾기 어렵다.
 *
 * 그래서 사람이 기억하는 대신 빌드가 막는다.
 */
class RequiredLevelVocabularyTest {

    private static final Path SQL_DIR = Paths.get("sql");
    private static final List<String> ALLOWED = List.of("ENTRY", "CORE", "ADVANCED", "EXPERT");

    /** 시드가 required_level 로 넣는 값을 뽑는다 — "'<값>' AS required_level" 형태. */
    private static final Pattern SEED_VALUE =
            Pattern.compile("'([A-Za-z_가-힣]*)'\s+AS\s+required_level");

    @Test
    @DisplayName("시드 파일의 요구 수준은 네 값만 쓴다")
    void 시드는_네_값만_쓴다() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path sql : sqlFiles()) {
            // 40번은 옛 값에서 새 값으로 옮기는 마이그레이션이라 옛 어휘가 나오는 게 정상이다
            if (sql.getFileName().toString().startsWith("40_")) {
                continue;
            }
            String text = Files.readString(sql);
            Matcher m = SEED_VALUE.matcher(text);
            while (m.find()) {
                String value = m.group(1);
                if (!ALLOWED.contains(value)) {
                    bad.add(sql.getFileName() + " : '" + value + "'");
                }
            }
        }
        assertEquals(List.of(), bad,
                "요구 수준은 ENTRY · CORE · ADVANCED · EXPERT 만 쓴다. "
                        + "옛 어휘(BASIC/INTERMEDIATE)가 남으면 새로 만든 DB 에서만 \"미지정\"으로 보인다.");
    }

    @Test
    @DisplayName("시드에 네 단계가 모두 들어 있다 — 한 칸이라도 비면 히트맵이 반쪽이 된다")
    void 시드가_네_단계를_모두_채운다() throws IOException {
        String seed = Files.readString(SQL_DIR.resolve("04_seed_skills.sql"));
        for (String level : ALLOWED) {
            assertTrue(seed.contains("'" + level + "' AS required_level"),
                    level + " 수준으로 요구되는 기술이 시드에 하나도 없다");
        }
    }

    private static List<Path> sqlFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SQL_DIR)) {
            return files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
    }
}
