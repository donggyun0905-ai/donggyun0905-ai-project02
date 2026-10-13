package com.specodyssey.service;

import com.specodyssey.dao.InsightDao.GapCellRow;
import com.specodyssey.dao.InsightDao.TrendRow;
import com.specodyssey.dto.InsightViewDto;
import com.specodyssey.dto.InsightViewDto.BenchmarkTier;
import com.specodyssey.dto.InsightViewDto.HeatRow;
import com.specodyssey.dto.InsightViewDto.HeatmapView;
import com.specodyssey.dto.InsightViewDto.Notice;
import com.specodyssey.dto.InsightViewDto.PeerView;
import com.specodyssey.dto.InsightViewDto.TrendSkill;
import com.specodyssey.dto.InsightViewDto.TrendView;
import com.specodyssey.service.JobBenchmarkSpecService.BenchmarkItem;
import com.specodyssey.service.JobBenchmarkSpecService.TierBenchmark;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InsightService 계산 단위테스트 (DB 불필요). 관련 요구사항: FR-45~48
 */
class InsightServiceTest {

    // ---------- FR-45 또래 비교 ----------

    @Test
    void 또래_평균과_비교한_문구를_만든다() {
        PeerView view = InsightService.comparePeers("컴퓨터공학", "4", new BigDecimal("62.00"), new BigDecimal("55.50"), 2);

        assertTrue(view.isComparable());
        assertEquals(62, view.myScore());
        assertEquals(56, view.peerAverage(), "55.5 → 반올림 56");
        assertEquals(2, view.peerCount());
        assertEquals("평균보다 6점 높습니다.", view.message());
    }

    @Test
    void 또래가_없거나_전공이_없으면_비교하지_않는다() {
        PeerView alone = InsightService.comparePeers("컴퓨터공학", "4", new BigDecimal("40"), null, 0);
        assertFalse(alone.isComparable());
        assertEquals(0, alone.peerCount());

        PeerView noMajor = InsightService.comparePeers(null, "4", new BigDecimal("40"), null, 0);
        assertFalse(noMajor.isComparable());
        assertTrue(noMajor.message().contains("전공과 학년"));

        PeerView noScore = InsightService.comparePeers("컴퓨터공학", "4", null, null, 0);
        assertFalse(noScore.isComparable());
    }

    @Test
    void 완성도_0점이면_비교하지_않고_프로필_안내() {
        PeerView view = InsightService.comparePeers("컴퓨터공학", "4", new BigDecimal("0.00"), new BigDecimal("50"), 1);
        assertFalse(view.isComparable());
        assertNull(view.myScore());
        assertTrue(view.message().contains("프로필"));
    }

    // ---------- 상단 안내 ----------

    @Test
    void 프로필이_빈_사용자는_프로필과_격차분석_안내를_받는다() {
        InsightViewDto view = new InsightViewDto();
        view.setPeer(InsightService.comparePeers("응소", "4", BigDecimal.ZERO, null, 0));
        view.setHeatmap(InsightService.buildHeatmap(List.of(new GapCellRow("언어", "BASIC", true))));

        List<Notice> notices = InsightService.buildNotices(view, true);
        assertEquals(List.of("/profile", "/profile", "/gap-analysis"), notices.stream().map(Notice::path).toList());
    }

    @Test
    void 데이터가_다_있으면_안내가_없다() {
        InsightViewDto view = new InsightViewDto();
        view.setPeer(InsightService.comparePeers("컴퓨터공학", "4", new BigDecimal("64"), new BigDecimal("57"), 1));
        view.setHeatmap(InsightService.buildHeatmap(List.of(new GapCellRow("언어", "BASIC", false))));
        assertTrue(InsightService.buildNotices(view, false).isEmpty());
    }

    // ---------- FR-47 요구 기술 변화 ----------

    @Test
    void 트렌드는_최신_달_비율로_정렬하고_전월_대비를_낸다() {
        List<TrendRow> rows = Arrays.asList(
                new TrendRow(10L, "Java", "202608", new BigDecimal("40.00")),
                new TrendRow(11L, "Spring", "202608", new BigDecimal("30.00")),
                new TrendRow(10L, "Java", "202609", new BigDecimal("35.40")),
                new TrendRow(11L, "Spring", "202609", new BigDecimal("38.60")),
                new TrendRow(12L, "Kafka", "202609", new BigDecimal("12.00")));
        TrendView view = InsightService.buildTrend(rows, 2);

        assertEquals(List.of("8월", "9월"), view.months());
        assertEquals(2, view.skills().size(), "상위 2개만");
        TrendSkill first = view.skills().get(0);
        assertEquals("Spring", first.skillName());
        assertEquals(Arrays.asList(30, 39), first.ratios());
        assertEquals(9, first.change());
        assertEquals(-5, view.skills().get(1).change(), "Java 40 → 35");
    }

    @Test
    void 지난달에_없던_기술은_신규로_표시하고_빈_데이터는_빈_트렌드() {
        List<TrendRow> rows = Arrays.asList(
                new TrendRow(10L, "Java", "202608", new BigDecimal("40")),
                new TrendRow(12L, "Kafka", "202609", new BigDecimal("12")));
        TrendSkill kafka = InsightService.buildTrend(rows, 5).skills().get(0);
        assertEquals("Kafka", kafka.skillName(), "최신 달에 없는 Java는 순위에서 빠진다");
        assertNull(kafka.change());
        assertEquals(Arrays.asList(null, 12), kafka.ratios());

        assertTrue(InsightService.buildTrend(Collections.emptyList(), 5).isEmpty());
    }

    // ---------- FR-46 합격자 참고 루트 ----------

    @Test
    void 참고_루트에_단계_표시명을_붙인다() {
        List<BenchmarkTier> tiers = InsightService.toBenchmarkTiers(Arrays.asList(
                new TierBenchmark("ENTRY", List.of(new BenchmarkItem("CERT", "정보처리기사"),
                        new BenchmarkItem("PROJECT", "Spring 프로젝트 1개"))),
                new TierBenchmark("CORE", List.of()),
                new TierBenchmark("EXPERT", List.of(new BenchmarkItem("SKILL", "오픈소스 기여")))));

        assertEquals(2, tiers.size(), "항목 없는 단계는 뺀다");
        assertEquals("입문", tiers.get(0).label());
        assertEquals(List.of("정보처리기사", "Spring 프로젝트 1개"), tiers.get(0).items());
        assertEquals("전문가", tiers.get(1).label());
        assertTrue(InsightService.toBenchmarkTiers(Collections.emptyList()).isEmpty());
    }

    // ---------- FR-48 약점 히트맵 ----------

    @Test
    void 히트맵은_분야와_수준별로_부족_수를_센다() {
        HeatmapView view = InsightService.buildHeatmap(Arrays.asList(
                new GapCellRow("데이터베이스", "BASIC", false),
                new GapCellRow("데이터베이스", "ADVANCED", true),
                new GapCellRow("클라우드", "INTERMEDIATE", true),
                new GapCellRow("클라우드", "INTERMEDIATE", true),
                new GapCellRow("클라우드", null, true),
                new GapCellRow(null, "BASIC", false)));

        assertEquals(List.of("기초", "중급", "심화", "미지정"), view.levels());
        assertEquals(4, view.missingTotal());
        assertEquals("클라우드 · 중급", view.weakest());

        HeatRow cloud = view.rows().stream().filter(r -> r.category().equals("클라우드")).findFirst().orElseThrow();
        assertEquals(2, cloud.cells().get(1).missing());
        assertEquals(3, cloud.cells().get(1).shade(), "가장 많이 부족한 칸이 가장 진하다");
        assertEquals(0, cloud.cells().get(0).total(), "요구하지 않는 칸");
        assertTrue(view.rows().stream().anyMatch(r -> r.category().equals("기타")), "분야 없는 기술은 기타로");
    }

    @Test
    void 히트맵_수준값이_모두_있으면_미지정_열은_없다() {
        HeatmapView view = InsightService.buildHeatmap(List.of(new GapCellRow("언어", "BASIC", false)));
        assertEquals(List.of("기초", "중급", "심화"), view.levels());
        assertEquals(0, view.missingTotal());
        assertNull(view.weakest());
        assertTrue(InsightService.buildHeatmap(Collections.emptyList()).isEmpty());
    }

    @Test
    void 음영은_최대_부족_수_기준_3단계() {
        assertEquals(0, InsightService.shade(0, 4));
        assertEquals(1, InsightService.shade(1, 4));
        assertEquals(2, InsightService.shade(2, 4));
        assertEquals(3, InsightService.shade(4, 4));
        assertEquals(0, InsightService.shade(0, 0));
    }
}
