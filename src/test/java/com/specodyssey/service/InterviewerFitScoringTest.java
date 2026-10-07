package com.specodyssey.service;

import com.specodyssey.dto.ShareViewDto.SkillItem;
import com.specodyssey.service.ShareViewService.ProjectEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 면접관 적합도의 숙련도 반영과 기술 → 프로젝트 근거 — DB 없이 도는 규칙 테스트. */
class InterviewerFitScoringTest {

    @Test
    void 숙련도와_프로젝트_근거에_따라_갖춘_기술을_다르게_인정한다() {
        assertEquals(1.0, InterviewerService.matchCredit("ADVANCED", false), 1e-9);
        assertEquals(0.8, InterviewerService.matchCredit("INTERMEDIATE", false), 1e-9);
        assertEquals(0.5, InterviewerService.matchCredit("BEGINNER", false), 1e-9);
        assertEquals(0.6, InterviewerService.matchCredit(null, false), 1e-9);
        // 프로젝트에서 써 봤으면 +0.2, 최대 1.0
        assertEquals(0.7, InterviewerService.matchCredit("BEGINNER", true), 1e-9);
        assertEquals(1.0, InterviewerService.matchCredit("ADVANCED", true), 1e-9);
        // 가중치 3짜리를 중급(0.8)으로 갖추고, 가중치 1짜리는 없음 → 2.4 ÷ 4 = 60
        assertEquals(60, InterviewerService.fitScore(3 * 0.8, 4));
    }

    @Test
    void 비교_표_칸에는_숙련도와_사용한_프로젝트_수를_적는다() {
        assertEquals("고급 · 프로젝트 2개", InterviewerService.matchDetail(
                new SkillItem("Java · 고급", 1L, "java", "ADVANCED", List.of("A", "B"))));
        assertEquals("숙련도 미입력", InterviewerService.matchDetail(
                new SkillItem("Java", 1L, "java", null, List.of())));
    }

    @Test
    void 기술_활용_설명이나_기술_스택_표기로_사용한_프로젝트를_찾는다() {
        List<ProjectEvidence> projects = List.of(
                new ProjectEvidence("도서 대여", Set.of(7013L), ProjectEvidence.stackKeys("Java, Spring Boot")),
                new ProjectEvidence("좌석 예약", Set.of(), ProjectEvidence.stackKeys("spring boot / MySQL · Docker")),
                new ProjectEvidence("통계", Set.of(), ProjectEvidence.stackKeys(null)));

        // 활용 설명의 skill_id로
        assertEquals(List.of("도서 대여"), ShareViewService.usedIn(projects, 7013L, "자바", null));
        // 입력 원문은 "스프링부트"여도 표준 이름 "Spring Boot"로 — 대소문자·구분자 무관
        assertEquals(List.of("도서 대여", "좌석 예약"),
                ShareViewService.usedIn(projects, 7034L, "스프링부트", "spring boot"));
        assertEquals(List.of("좌석 예약"), ShareViewService.usedIn(projects, null, "docker", null));
        assertEquals(List.of(), ShareViewService.usedIn(projects, null, "redis", null));
    }
}
