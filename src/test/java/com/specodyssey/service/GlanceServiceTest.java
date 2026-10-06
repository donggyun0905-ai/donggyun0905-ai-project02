package com.specodyssey.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "한눈에 보기" 위젯의 문구 다듬기 — 좁은 칸에 맞게 줄인다. */
class GlanceServiceTest {

    @Test
    void 프로젝트_아이디어는_앞머리와_설명을_떼고_제목만_남긴다() {
        assertEquals("도서 대출 관리 API",
                GlanceService.shortReason("아이디어: 도서 대출 관리 API — Spring Boot와 MySQL로 대출·반납을 만드는 프로젝트입니다."));
    }

    @Test
    void 일반_설명은_첫_문장만_남긴다() {
        assertEquals("BACKEND 직무에서 기본 요건으로 자주 요구되는 자격증입니다.",
                GlanceService.shortReason("BACKEND 직무에서 기본 요건으로 자주 요구되는 자격증입니다. 추가 설명이 이어집니다."));
    }

    @Test
    void 너무_길면_말줄임표로_자른다() {
        String shortened = GlanceService.shortReason("가".repeat(100));
        assertEquals(GlanceService.STEP_TEXT_MAX, shortened.length());
        assertTrue(shortened.endsWith("…"));
    }

    @Test
    void 비어_있으면_빈_문자열() {
        assertEquals("", GlanceService.shortReason(null));
        assertEquals("", GlanceService.shortReason("   "));
    }

    @Test
    void JSP가_읽는_getter는_record_값과_같다() {
        GlanceService.UpcomingDday dday = new GlanceService.UpcomingDday("SQLD 시험", 3);
        GlanceService.Glance g = new GlanceService.Glance(java.util.List.of(dday), "기술", "Java 공부",
                "취준생", "방랑자", 820, "실전러", 680, 32);
        assertEquals(dday.title(), dday.getTitle());
        assertEquals(dday.daysLeft(), dday.getDaysLeft());
        assertEquals(g.ddays(), g.getDdays());
        assertEquals(g.nextStepText(), g.getNextStepText());
        assertEquals(g.tierPercent(), g.getTierPercent());
        assertEquals(g.pointsToNextTier(), g.getPointsToNextTier());
    }

    @Test
    void 기술_단계는_티어마다_할_일이_다르다() {
        assertEquals("공부노트 쓰기", GlanceService.skillAction("ENTRY"));
        assertEquals("프로젝트에 써 보기", GlanceService.skillAction("CORE"));
        assertEquals("프로젝트 업그레이드", GlanceService.skillAction("ADVANCED"));
        assertEquals("기술 설명 글 쓰기", GlanceService.skillAction("EXPERT"));
    }

    @Test
    void 단계_종류는_한글_이름으로() {
        assertEquals("자격증", GlanceService.stepTypeLabel("CERT"));
        assertEquals("트렌딩 학습", GlanceService.stepTypeLabel("TREND_STUDY"));
        assertEquals("단계", GlanceService.stepTypeLabel("UNKNOWN"));
    }
}
