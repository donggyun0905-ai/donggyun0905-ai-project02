package com.specodyssey.service;

import com.specodyssey.dto.UserSkillDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 격차 분석 "숙련도" 칸 — 요구 기술과 프로필 기술을 이어 입문·중급·고급으로 보여 주는 단위테스트.
 */
class GapSkillLevelServiceTest {

    @Test
    void skill_id로_연결된_기술의_숙련도() {
        List<UserSkillDto> owned = List.of(skill(10L, "Docker", "INTERMEDIATE"), skill(11L, "Redis", "ADVANCED"));

        assertEquals("중급", GapSkillLevelService.levelLabel(owned, 10L, "Docker"));
        assertEquals("고급", GapSkillLevelService.levelLabel(owned, 11L, "Redis"));
    }

    @Test
    void 직접_입력한_기술은_이름으로_찾는다() {
        List<UserSkillDto> owned = List.of(skill(null, "  java ", "BEGINNER"));

        assertEquals("입문", GapSkillLevelService.levelLabel(owned, 1L, "Java"));
    }

    @Test
    void 프로필에_없으면_대시_숙련도를_안_골랐으면_미입력() {
        List<UserSkillDto> owned = List.of(skill(5L, "Git", null));

        assertEquals("—", GapSkillLevelService.levelLabel(owned, 99L, "Kubernetes"));
        assertEquals("미입력", GapSkillLevelService.levelLabel(owned, 5L, "Git"));
        assertEquals("—", GapSkillLevelService.levelLabel(List.of(), 5L, "Git"));
    }

    @Test
    void 같은_기술이_두_줄이면_높은_숙련도() {
        List<UserSkillDto> owned = List.of(skill(null, "MySQL", "BEGINNER"), skill(7L, "MySQL", "ADVANCED"));

        assertEquals("고급", GapSkillLevelService.levelLabel(owned, 7L, "MySQL"));
    }

    private static UserSkillDto skill(Long skillId, String rawInput, String proficiency) {
        UserSkillDto dto = new UserSkillDto();
        dto.setSkillId(skillId);
        dto.setRawInput(rawInput);
        dto.setProficiency(proficiency);
        return dto;
    }
}
