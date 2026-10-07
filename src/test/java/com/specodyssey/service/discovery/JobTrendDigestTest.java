package com.specodyssey.service.discovery;

import com.specodyssey.dao.InsightDao.TrendRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** FR-35 전망 근거 계산. DB 없이 TrendRow만으로 확인한다. */
class JobTrendDigestTest {

    private static TrendRow row(long id, String name, String ym, double ratio) {
        return new TrendRow(id, name, ym, BigDecimal.valueOf(ratio));
    }

    /** 8월·9월 각 6개 기술 — Docker +12, AWS +6, Kotlin +3(문턱 미만), Java -5 */
    private static List<TrendRow> backendTwoMonths() {
        List<TrendRow> rows = new ArrayList<>();
        rows.add(row(1, "Java", "202608", 60));
        rows.add(row(2, "Spring Boot", "202608", 50));
        rows.add(row(3, "MySQL", "202608", 40));
        rows.add(row(4, "Docker", "202608", 20));
        rows.add(row(5, "AWS", "202608", 15));
        rows.add(row(6, "Kotlin", "202608", 10));
        rows.add(row(1, "Java", "202609", 55));
        rows.add(row(2, "Spring Boot", "202609", 50));
        rows.add(row(3, "MySQL", "202609", 41));
        rows.add(row(4, "Docker", "202609", 32));
        rows.add(row(5, "AWS", "202609", 21));
        rows.add(row(6, "Kotlin", "202609", 13));
        return rows;
    }

    @Test
    void 최신_달_상위_기술과_늘어난_기술을_고른다() {
        JobTrendDigest d = JobTrendDigest.from(backendTwoMonths());

        assertEquals("9월", d.getMonth());
        assertEquals(List.of("Java", "Spring Boot", "MySQL"), d.getHot());
        assertEquals(List.of("Docker", "AWS"), d.getRising().stream().map(JobTrendDigest.Rising::getName).toList());
        assertEquals(12, d.getRising().get(0).getChange());
    }

    @Test
    void 늘어난_폭이_문턱보다_작으면_늘어난_기술이_아니다() {
        JobTrendDigest d = JobTrendDigest.from(backendTwoMonths());

        assertTrue(d.getRising().stream().noneMatch(r -> r.getName().equals("Kotlin")), "+3%p는 문턱(5) 미만");
    }

    @Test
    void 최신_달_기술이_너무_적으면_근거로_쓰지_않는다() {
        // 실제 IT 기획자처럼 한 달에 2건뿐인 직무 — 비율이 크게 튀어 근거가 안 된다
        List<TrendRow> thin = List.of(row(1, "Jira", "202609", 100), row(2, "SQL", "202609", 50));

        assertNull(JobTrendDigest.from(thin));
        assertNull(JobTrendDigest.from(List.of()));
        assertNull(JobTrendDigest.from(null));
    }

    @Test
    void 한_달치만_있으면_많이_찾는_기술만_보여준다() {
        List<TrendRow> oneMonth = backendTwoMonths().stream().filter(r -> r.periodYm().equals("202609")).toList();

        JobTrendDigest d = JobTrendDigest.from(oneMonth);

        assertEquals(3, d.getHot().size());
        assertTrue(d.getRising().isEmpty());
    }

    @Test
    void 지난달_데이터가_빈약하면_늘었다고_하지_않는다() {
        List<TrendRow> rows = new ArrayList<>(backendTwoMonths().stream().filter(r -> r.periodYm().equals("202609")).toList());
        rows.add(row(4, "Docker", "202608", 1)); // 8월은 1건뿐 → Docker +31로 보이는 착시

        assertTrue(JobTrendDigest.from(rows).getRising().isEmpty());
    }

    @Test
    void 프롬프트에는_기술_이름만_들어가고_숫자는_없다() {
        String line = JobTrendDigest.from(backendTwoMonths()).promptLine();

        assertTrue(line.contains("Docker"));
        assertFalse(line.matches(".*\\d.*"), "LLM에 숫자를 주면 전망에 수치를 쓰기 쉽다: " + line);
    }
}
