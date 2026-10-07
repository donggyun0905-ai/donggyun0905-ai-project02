package com.specodyssey.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 요구사항(FR) ↔ 코드·테스트 연결표를 만들어 리소스에 넣어 둔다 (2026-10-07).
 *
 * 배포된 WAR에는 src·docs가 없어 관리자 화면이 실행 중에 소스를 읽을 수 없다 — 그래서 여기서 만든
 * 결과를 src/main/resources/feature-coverage.json에 두고 화면은 그 리소스만 읽는다.
 *
 * 소스가 바뀌어 표가 달라지면 파일을 새로 써 두고 이 테스트가 실패한다 — 다시 돌리면 통과하고,
 * 바뀐 파일을 커밋하면 된다. 이렇게 해 두면 "문서가 코드보다 낡는" 일이 생기지 않는다.
 */
class FeatureCoverageTest {

    private static final Path OUTPUT = Paths.get("src/main/resources/feature-coverage.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Test
    void 요구사항_코드_연결표가_지금_소스와_같다() throws Exception {
        List<FeatureCoverageScanner.Feature> features = FeatureCoverageScanner.scan();
        // 요구사항 명세서의 FR 표는 53줄이다(나머지 FR 언급은 NFR 표·본문) — 표 형식이 바뀌면 여기서 걸린다
        assertTrue(features.size() >= 50, "요구사항 표를 못 읽었다(형식 확인): " + features.size());

        String fresh = GSON.toJson(features) + "\n";
        String committed = Files.exists(OUTPUT) ? Files.readString(OUTPUT) : "";
        if (!fresh.equals(committed)) {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, fresh);
            throw new AssertionError(OUTPUT + " 가 지금 소스와 달라 새로 썼습니다. "
                    + "테스트를 다시 돌리면 통과하고, 바뀐 파일을 함께 커밋하세요.");
        }
    }

    @Test
    void FR_참조는_범위와_생략_표기까지_펼친다() {
        assertEquals(Set.of("FR-13"), FeatureCoverageScanner.extractFrIds("관련 요구사항: FR-13"));
        assertEquals(Set.of("FR-41", "FR-42", "FR-43", "FR-44"),
                FeatureCoverageScanner.extractFrIds("대시보드 화면. 관련 요구사항: FR-41~44"));
        assertEquals(Set.of("FR-71", "FR-72"),
                FeatureCoverageScanner.extractFrIds("D-day 알림 화면. 관련 요구사항: FR-71 · 72"));
        assertEquals(Set.of("FR-23", "FR-25"), FeatureCoverageScanner.extractFrIds("(FR-23·25, TD-1)"));
        assertTrue(FeatureCoverageScanner.extractFrIds("요구사항 번호가 없는 주석").isEmpty());
    }

    @Test
    void 핵심_기능은_구현_산출물이_연결돼_있다() throws Exception {
        List<FeatureCoverageScanner.Feature> features = FeatureCoverageScanner.scan();

        // 프로젝트의 심장(FR-31 격차 분석 · FR-32 로드맵)과 가입·로그인은 반드시 연결돼 있어야 한다
        for (String id : List.of("FR-11", "FR-12", "FR-31", "FR-32")) {
            FeatureCoverageScanner.Feature feature = features.stream()
                    .filter(f -> f.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError(id + " 없음"));
            assertTrue(feature.implemented(), id + "(" + feature.title() + ")에 연결된 코드가 없다");
            assertFalse(feature.tests().isEmpty(), id + "(" + feature.title() + ")에 연결된 테스트가 없다");
        }
    }
}
