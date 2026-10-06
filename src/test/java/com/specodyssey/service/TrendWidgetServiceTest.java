package com.specodyssey.service;

import com.specodyssey.dao.TrendCollectDao;
import com.specodyssey.dto.TrendTechDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 트렌드 위젯 고르기 규칙 — 목표 직무 → 같은 계열 → 없음(엉뚱한 기술은 안 보여줌), 직무가 없을 때만 최근 트렌드. */
class TrendWidgetServiceTest {

    private static TrendTechDto tech(String name) {
        TrendTechDto t = new TrendTechDto();
        t.setId((long) name.hashCode());
        t.setTechName(name);
        return t;
    }

    private static List<String> names(TrendWidgetService.TrendWidget widget) {
        return widget.items().stream().map(TrendTechDto::getTechName).toList();
    }

    /** DB 없이 세 조회의 결과만 정해 두는 가짜 DAO */
    private static final class FakeDao extends TrendCollectDao {
        final List<TrendTechDto> own;
        final List<TrendTechDto> siblings;
        final List<TrendTechDto> recent;
        final List<Double> askedRelevance = new ArrayList<>();

        FakeDao(List<TrendTechDto> own, List<TrendTechDto> siblings, List<TrendTechDto> recent) {
            this.own = own;
            this.siblings = siblings;
            this.recent = recent;
        }

        @Override
        public List<TrendTechDto> findRelevantByJobId(Long jobId, double minRelevance, int limit) {
            askedRelevance.add(minRelevance);
            return own.subList(0, Math.min(limit, own.size()));
        }

        @Override
        public List<TrendTechDto> findRelevantBySiblingJobs(Long jobId, double minRelevance, int limit) {
            askedRelevance.add(minRelevance);
            return siblings.subList(0, Math.min(limit, siblings.size()));
        }

        @Override
        public List<TrendTechDto> findRecent(int limit) {
            return recent.subList(0, Math.min(limit, recent.size()));
        }
    }

    @Test
    void 목표_직무_트렌드가_충분하면_그것만_보여준다() throws Exception {
        FakeDao dao = new FakeDao(List.of(tech("Java"), tech("Spring Boot"), tech("Redis"), tech("Go")),
                List.of(tech("Kafka")), List.of(tech("Unrelated")));
        TrendWidgetService.TrendWidget widget = new TrendWidgetService(dao).forJob(1L, 3);

        assertEquals(List.of("Java", "Spring Boot", "Redis"), names(widget));
        assertEquals(TrendWidgetService.Source.JOB, widget.source());
        assertTrue(dao.askedRelevance.stream().allMatch(r -> r >= 0.8), "관련도 낮은 연결은 처음부터 걸러야 한다");
    }

    @Test
    void 모자라면_같은_계열_직무의_트렌드로_채우고_같은_기술은_한_번만_보여준다() throws Exception {
        FakeDao dao = new FakeDao(List.of(tech("React")),
                List.of(tech("react"), tech("TypeScript"), tech("Three.js")), List.of(tech("Unrelated")));
        TrendWidgetService.TrendWidget widget = new TrendWidgetService(dao).forJob(1L, 3);

        assertEquals(List.of("React", "TypeScript", "Three.js"), names(widget));
        assertEquals(TrendWidgetService.Source.JOB_AND_CATEGORY, widget.source());
    }

    @Test
    void 목표_직무에_연결된_게_없으면_같은_계열만으로_보여준다() throws Exception {
        FakeDao dao = new FakeDao(List.of(), List.of(tech("WebAuthn"), tech("RSA")), List.of(tech("Unrelated")));
        TrendWidgetService.TrendWidget widget = new TrendWidgetService(dao).forJob(1L, 3);

        assertEquals(List.of("WebAuthn", "RSA"), names(widget));
        assertEquals(TrendWidgetService.Source.CATEGORY, widget.source());
    }

    @Test
    void 직무도_계열도_없으면_엉뚱한_최근_트렌드를_보여주지_않는다() throws Exception {
        FakeDao dao = new FakeDao(List.of(), List.of(), List.of(tech("Unrelated")));
        TrendWidgetService.TrendWidget widget = new TrendWidgetService(dao).forJob(1L, 3);

        assertTrue(widget.items().isEmpty());
        assertEquals(TrendWidgetService.Source.NONE, widget.source());
    }

    @Test
    void 목표_직무가_없을_때만_최근_트렌드를_보여준다() throws Exception {
        FakeDao dao = new FakeDao(List.of(tech("Java")), List.of(), List.of(tech("Supabase"), tech("rsync")));
        TrendWidgetService.TrendWidget widget = new TrendWidgetService(dao).forJob(null, 3);

        assertEquals(List.of("Supabase", "rsync"), names(widget));
        assertEquals(TrendWidgetService.Source.GENERAL, widget.source());
    }
}
